package cassdemo.scheduling;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import cassdemo.entities.DoctorAppointment;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import static cassdemo.util.Util.generateUUID;

public class AppointmentSchedulerThread extends Thread {

    private final ClinicBackend clinicBackend;
    private final String specialty;
    private final int id;
    private static final Logger logger = LoggerFactory.getLogger(ClinicBackend.class);

    public AppointmentSchedulerThread(ClinicBackend clinicBackend, String specialty) {
        this.clinicBackend = clinicBackend;
        this.specialty = specialty;
        this.id = generateUUID();
    }

    @Override
    public void run() {
        try {
            logger.info("Starting scheduling for specialty " + specialty + "...");
            while (true) {
                scheduleForSpecialty(specialty);
                Thread.sleep(100);
            }
        } catch (BackendException e) {
            logger.error("Backend error when scheduling for specialty: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Interruption error when scheduling for specialty: " + e.getMessage());
        }
    }

    private void scheduleForSpecialty(String specialty) throws BackendException, InterruptedException {
        ResultSet pendingAppointments = clinicBackend.selectPendingAppointments(specialty);
        boolean schedulingWasSuccessful = false;

        while (!pendingAppointments.isExhausted() && !schedulingWasSuccessful) {
            Row appointmentRow = pendingAppointments.one();
            int appointmentId = appointmentRow.getInt("appointment_id");
            int priority = appointmentRow.getInt("priority");
            Date timestamp = appointmentRow.getTimestamp("timestamp");
            logger.info("Now processing appointment with id " + appointmentId + ", specialty " + specialty);
            logger.info("Trying to check for ownership of appointment " + appointmentId);
            Row appointmentOwnership = clinicBackend.selectOwnership(appointmentId);
            if (appointmentOwnership != null && appointmentOwnership.getInt("scheduler_id") != this.id) {
                logger.info("Appointment already owned by scheduler " + appointmentOwnership.getInt("scheduler_id") + " My own id is " + this.id);
                continue;
            }
            clinicBackend.claimAppointmentOwnership(appointmentId, this.id);
            Thread.sleep(100);
            appointmentOwnership = clinicBackend.selectOwnership(appointmentId);
            if (appointmentOwnership != null && appointmentOwnership.getInt("scheduler_id") != this.id) {
                logger.info("Appointment already owned by scheduler " + appointmentOwnership.getInt("scheduler_id"));
                continue;
            }

            findAvailableDoctor(specialty, appointmentId, priority);
            clinicBackend.deleteAppointment(specialty, priority, timestamp, appointmentId);
            clinicBackend.deleteOwnership(appointmentId);
            schedulingWasSuccessful = true;
        }

    }

    private void findAvailableDoctor(String specialty, int appointmentId, int priority) throws BackendException, InterruptedException {
        LocalDateTime bestAvailableSlot = null;
        int bestDoctorId = -1;

        ResultSet doctors = clinicBackend.getDoctorsBySpecialty(specialty);
        boolean appointmentInsertionSuccessfull = false;
        boolean evictionPossible = false;
        while (!appointmentInsertionSuccessfull) {
            for (Row doc : doctors) {
                int doctorId = doc.getInt("doctor_id");
                LocalTime startHours = Time.valueOf(doc.getString("start_hours")).toLocalTime();
                LocalTime endHours = Time.valueOf(doc.getString("end_hours")).toLocalTime();
                LocalDateTime firstAvailableSlot = LocalDate.now().plusDays(1).atTime(startHours);

                Row result = clinicBackend.selectLatestDoctorAppointment(doctorId);
                boolean localEvictionPossible = false;
                if (result != null) {
                    com.datastax.driver.core.LocalDate slotDate = result.getDate("appointment_date");
                    LocalDate slotLocalDate = LocalDate.of(slotDate.getYear(), slotDate.getMonth(), slotDate.getDay());
                    Time slotTime = new Time(result.getTime("time_slot") / 1000000);
                    LocalTime slotLocalTime = slotTime.toLocalTime();
                    if (slotLocalTime.plusHours(2).isAfter(endHours)) {
                        firstAvailableSlot = slotLocalDate.plusDays(1).atTime(startHours);
                    } else {
                        firstAvailableSlot = slotLocalDate.atTime(slotLocalTime.plusMinutes(30));
                        localEvictionPossible = true;
                    }
                }

                if (bestAvailableSlot == null || bestAvailableSlot.isAfter(firstAvailableSlot)) {
                    bestAvailableSlot = firstAvailableSlot;
                    bestDoctorId = doctorId;
                    evictionPossible = localEvictionPossible;
                }
            }
            DoctorAppointment evictionCandidate = null;
            if (evictionPossible && priority < 3) {
                ResultSet existingDoctorAppointments = clinicBackend.getDoctorDaySchedule(bestDoctorId, bestAvailableSlot.toLocalDate());
                for (Row existingDoctorAppointment : existingDoctorAppointments) {
                    int existingAppointmentPriority = existingDoctorAppointment.getInt("priority");
                    if (existingAppointmentPriority < priority) {
                        Time slotTime = new Time(existingDoctorAppointment.getTime("time_slot") / 1000000);
                        LocalTime slotLocalTime = slotTime.toLocalTime();
                        com.datastax.driver.core.LocalDate slotDate = existingDoctorAppointment.getDate("appointment_date");
                        LocalDate slotLocalDate = LocalDate.of(slotDate.getYear(), slotDate.getMonth(), slotDate.getDay());
                        int existingAppointmentId = existingDoctorAppointment.getInt("appointment_id");
                        evictionCandidate = new DoctorAppointment(bestDoctorId, slotLocalDate, slotLocalTime, existingAppointmentId, existingAppointmentPriority);
                        break;
                    }
                }
                if (evictionCandidate == null) {
                    evictionPossible = false;
                } else {
                    clinicBackend.scheduleDoctorAppointment(bestDoctorId, evictionCandidate.appointmentId, bestAvailableSlot, evictionCandidate.priority);
                    Thread.sleep(100);
                    Row slotContent = clinicBackend.checkScheduleSlot(bestDoctorId, bestAvailableSlot.toLocalDate(), bestAvailableSlot.toLocalTime());
                    if (slotContent.getInt("appointment_id") != appointmentId) {
                        logger.info("Failed to evict and insert doctor appointment for doctor " + bestDoctorId + ". Appointment " + slotContent.getInt("appointment_id") + " is already there");
                    } else {
                        clinicBackend.updateDoctorAppointment(bestDoctorId, evictionCandidate.appointmentDate, evictionCandidate.timeSlot, appointmentId, priority);
                        logger.info("DoctorAppointment " + evictionCandidate.appointmentId + " evicted and re-scheduled for " + bestAvailableSlot);
                        appointmentInsertionSuccessfull = true;
                    }
                }
            }
            if (!evictionPossible) {
                clinicBackend.scheduleDoctorAppointment(bestDoctorId, appointmentId, bestAvailableSlot, priority);
                Thread.sleep(100);
                Row slotContent = clinicBackend.checkScheduleSlot(bestDoctorId, bestAvailableSlot.toLocalDate(), bestAvailableSlot.toLocalTime());
                if (slotContent.getInt("appointment_id") != appointmentId) {
                    logger.info("Failed to insert doctor appointment for doctor " + bestDoctorId + ". Appointment " + slotContent.getInt("appointment_id") + " is already there");
                } else {
                    appointmentInsertionSuccessfull = true;
                }
            }

        }
    }
}
