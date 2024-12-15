package cassdemo.scheduling;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

public class AppointmentSchedulerThread extends Thread {

    private final ClinicBackend clinicBackend;
    private final String specialty;
    private static final Logger logger = LoggerFactory.getLogger(ClinicBackend.class);

    public AppointmentSchedulerThread(ClinicBackend clinicBackend, String specialty) {
        this.clinicBackend = clinicBackend;
        this.specialty = specialty;
    }

    @Override
    public void run() {
        try {
            logger.info("Starting scheduling for specialty " + specialty + "...");
            while (true) {
                scheduleForSpecialty(specialty);
                Thread.sleep(1000);
            }
        } catch (BackendException e) {
            logger.error("Backend error when scheduling for specialty: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Interruption error when scheduling for specialty: " + e.getMessage());
        }
    }

    private void scheduleForSpecialty(String specialty) throws BackendException {
        Row appointmentRow = clinicBackend.selectNextAppointment(specialty);
        if (appointmentRow == null) return;

        int appointmentId = appointmentRow.getInt("appointment_id");
        int priority = appointmentRow.getInt("priority");
        Date timestamp = appointmentRow.getTimestamp("timestamp");
        logger.info("Now processing appointment with id " + appointmentId + ", specialty " + specialty);
        findAvailableDoctor(specialty, appointmentId);
        clinicBackend.deleteAppointment(specialty, priority, timestamp, appointmentId);
    }

    private void findAvailableDoctor(String specialty, int appointmentId) throws BackendException {
        LocalDateTime bestAvailableSlot = null;
        int bestDoctorId = -1;

        ResultSet rs = clinicBackend.getDoctorsBySpecialty(specialty);
        for (Row row : rs) {
            int doctorId = row.getInt("doctor_id");
            LocalTime startHours = Time.valueOf(row.getString("start_hours")).toLocalTime();
            LocalTime endHours = Time.valueOf(row.getString("end_hours")).toLocalTime();
            LocalDateTime firstAvailableSlot = LocalDate.now().plusDays(1).atTime(startHours);

            Row result = clinicBackend.selectLatestDoctorAppointment(doctorId);

            if (result != null) {
                com.datastax.driver.core.LocalDate slotDate = result.getDate("appointment_date");
                LocalDate slotLocalDate = LocalDate.of(slotDate.getYear(), slotDate.getMonth(), slotDate.getDay());
                Time slotTime = new Time(result.getTime("time_slot") / 1000000);
                logger.info("slot time: " + slotTime);
                LocalTime slotLocalTime = slotTime.toLocalTime();
                logger.info("slot local time: " + slotLocalTime);

                if (slotLocalTime.plusHours(2).isAfter(endHours)) {
                    firstAvailableSlot = slotLocalDate.plusDays(1).atTime(startHours);
                } else {
                    firstAvailableSlot = slotLocalDate.atTime(slotLocalTime.plusMinutes(30));
                }
            }

            if (bestAvailableSlot == null || bestAvailableSlot.isAfter(firstAvailableSlot)) {
                bestAvailableSlot = firstAvailableSlot;
                bestDoctorId = doctorId;
            }
        }
        clinicBackend.scheduleDoctorAppointment(bestDoctorId, appointmentId, bestAvailableSlot);
    }
}
