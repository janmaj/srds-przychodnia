package cassdemo.scheduling;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import cassdemo.entities.Appointment;
import cassdemo.entities.AppointmentOwnership;
import cassdemo.entities.Doctor;
import cassdemo.entities.DoctorAppointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

import static cassdemo.util.Util.generateUUID;

public class AppointmentSchedulerThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentSchedulerThread.class);
    private final ClinicBackend clinicBackend;
    private final String specialty;
    private final int id;
    private Appointment processedAppointment;
    private volatile boolean interrupted = false;

    public AppointmentSchedulerThread(ClinicBackend clinicBackend, String specialty) {
        this.clinicBackend = clinicBackend;
        this.specialty = specialty;
        this.id = generateUUID();
    }

    @Override
    public void run() {
        try {
            logger.info("Starting scheduling for specialty " + specialty + "...");
            while (!interrupted) {
                scheduleForSpecialty(specialty);
                Thread.sleep(100);
            }
        } catch (BackendException e) {
            logger.error("Backend error when scheduling for specialty: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Interruption error when scheduling for specialty: " + e.getMessage());
        } finally {
            logger.info("Performing cleanup...");
            if (processedAppointment != null) {
                clinicBackend.deleteOwnership(processedAppointment.appointmentId);
            }
        }
    }

    private void scheduleForSpecialty(String specialty) throws BackendException, InterruptedException {
        List<Appointment> pendingAppointments = clinicBackend.selectPendingAppointments(specialty);
        boolean schedulingWasSuccessful = false;

        Iterator<Appointment> it = pendingAppointments.iterator();
        while (it.hasNext() && !schedulingWasSuccessful) {
            processedAppointment = it.next();
            logger.info("Now processing appointment with id " + processedAppointment.appointmentId + ", specialty " + specialty);
            logger.info("Checking ownership for appointment " + processedAppointment.appointmentId);
            AppointmentOwnership appointmentOwnership = clinicBackend.selectOwnership(processedAppointment.appointmentId);
            if (appointmentOwnership != null && appointmentOwnership.schedulerId != this.id) {
                logger.info("Appointment already owned by scheduler " + appointmentOwnership.schedulerId + ". Backing off...");
                continue;
            }
            clinicBackend.claimAppointmentOwnership(processedAppointment.appointmentId, this.id);
            Thread.sleep(100);
            appointmentOwnership = clinicBackend.selectOwnership(processedAppointment.appointmentId);
            if (appointmentOwnership != null && appointmentOwnership.schedulerId != this.id) {
                logger.info("Appointment already owned by scheduler " + appointmentOwnership.schedulerId + ". Backing off...");
                continue;
            }
            logger.info("Successfully claimed ownership of " + processedAppointment.appointmentId + ". Now trying to schedule");

            findAvailableDoctor(specialty, processedAppointment.appointmentId, processedAppointment.priority, processedAppointment.patientFirstName, processedAppointment.patientLastName);
            logger.info("Successfully scheduled appointment " + processedAppointment.appointmentId);
            clinicBackend.deleteAppointment(processedAppointment);
            clinicBackend.deleteOwnership(processedAppointment.appointmentId);
            schedulingWasSuccessful = true;
        }

    }

    private void findAvailableDoctor(String specialty, int appointmentId, int priority, String patientName, String patientLastName) throws BackendException, InterruptedException {
        LocalDateTime bestAvailableSlot = null;
        int bestDoctorId = -1;

        List<Doctor> doctors = clinicBackend.getDoctorsBySpecialty(specialty);
        boolean appointmentInsertionSuccessfull = false;
        boolean evictionPossible = false;
        while (!appointmentInsertionSuccessfull) {
            for (Doctor doc : doctors) {
                LocalDateTime firstAvailableSlot = LocalDate.now().plusDays(1).atTime(doc.startHours);

                DoctorAppointment latestAppointment = clinicBackend.selectLatestDoctorAppointment(doc.doctorId);
                boolean localEvictionPossible = false;
                if (latestAppointment != null) {
                    if (latestAppointment.timeSlot.plusHours(1).isAfter(doc.endHours)) {
                        firstAvailableSlot = latestAppointment.appointmentDate.plusDays(1).atTime(doc.startHours);
                    } else {
                        firstAvailableSlot = latestAppointment.appointmentDate.atTime(latestAppointment.timeSlot.plusMinutes(30));
                        localEvictionPossible = true;
                    }
                }

                if (bestAvailableSlot == null || bestAvailableSlot.isAfter(firstAvailableSlot)) {
                    bestAvailableSlot = firstAvailableSlot;
                    bestDoctorId = doc.doctorId;
                    evictionPossible = localEvictionPossible;
                }
            }
            DoctorAppointment evictionCandidate = null;
            if (evictionPossible && priority < 3) {
                logger.info("Eviction possible. Looking for an appointment to evict");
                List<DoctorAppointment> existingDoctorAppointments = clinicBackend.getDoctorDaySchedule(bestDoctorId, bestAvailableSlot.toLocalDate());
                for (DoctorAppointment existingDoctorAppointment : existingDoctorAppointments) {
                    if (existingDoctorAppointment.priority > priority) {
                        evictionCandidate = existingDoctorAppointment;
                        logger.info("Found eviction candidate " + evictionCandidate.appointmentId);
                        break;
                    }
                }
                if (evictionCandidate == null) {
                    evictionPossible = false;
                } else {
                    logger.info("Trying to evict appointment " + evictionCandidate.appointmentId + " and replace it by " + appointmentId);
                    clinicBackend.scheduleDoctorAppointment(bestDoctorId, evictionCandidate.appointmentId, bestAvailableSlot, evictionCandidate.priority, evictionCandidate.patientName, evictionCandidate.patientLastName);
                    Thread.sleep(100);
                    DoctorAppointment slotContent = clinicBackend.checkScheduleSlot(bestDoctorId, bestAvailableSlot.toLocalDate(), bestAvailableSlot.toLocalTime());
                    if (slotContent.appointmentId != evictionCandidate.appointmentId) {
                        logger.info("Failed to evict and insert doctor appointment for doctor " + bestDoctorId + ". Appointment " + slotContent.appointmentId + " is already there");
                    } else {
                        clinicBackend.updateDoctorAppointment(bestDoctorId, evictionCandidate.appointmentDate, evictionCandidate.timeSlot, appointmentId, priority, evictionCandidate.patientName, evictionCandidate.patientLastName);
                        logger.info("DoctorAppointment " + evictionCandidate.appointmentId + " evicted and re-scheduled for " + bestAvailableSlot);
                        appointmentInsertionSuccessfull = true;
                    }
                }
            }
            if (!evictionPossible || priority == 3) {
                logger.info("Eviction not possible. Using traditional insert...");
                clinicBackend.scheduleDoctorAppointment(bestDoctorId, appointmentId, bestAvailableSlot, priority, patientName, patientLastName);
                Thread.sleep(100);
                DoctorAppointment slotContent = clinicBackend.checkScheduleSlot(bestDoctorId, bestAvailableSlot.toLocalDate(), bestAvailableSlot.toLocalTime());
                if (slotContent.appointmentId != appointmentId) {
                    logger.info("Failed to insert doctor appointment for doctor " + bestDoctorId + ". Appointment " + slotContent.appointmentId + " is already there");
                } else {
                    appointmentInsertionSuccessfull = true;
                }
            }

        }
    }

    public void stopScheduling() {
        this.interrupted = true;
    }
}
