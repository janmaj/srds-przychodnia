package cassdemo.backend;

import com.datastax.driver.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MedicalScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MedicalScheduler.class);
    // Prepared statements
    private static PreparedStatement INSERT_APPOINTMENT;
    private static PreparedStatement INSERT_DOCTOR_APPOINTMENT;
    private static PreparedStatement SELECT_NEXT_APPOINTMENT;
    private static PreparedStatement SELECT_DOCTOR_BY_SPECIALTY;
    private static PreparedStatement CHECK_DOCTOR_AVAILABILITY;
    private static PreparedStatement INSERT_DOCTOR;
    private static PreparedStatement DELETE_APPOINTMENT;
    private static PreparedStatement SELECT_LATEST_DOCTOR_APPOINTMENT;
    private final Session session;

    // Constructor
    public MedicalScheduler(String contactPoint, String keyspace) throws BackendException {
        Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
        try {
            session = cluster.connect(keyspace);
        } catch (Exception e) {
            throw new BackendException("Could not connect to the cluster. " + e.getMessage(), e);
        }
        prepareStatements();
    }

    private void prepareStatements() throws BackendException {
        try {
            INSERT_APPOINTMENT = session.prepare("INSERT INTO Appointments (specialty, priority, appointment_id, patient_first_name, patient_last_name, timestamp) " + "VALUES (?, ?, ?, ?, ?, ?);");

            INSERT_DOCTOR_APPOINTMENT = session.prepare("INSERT INTO DoctorAppointments (doctor_id, appointment_date, time_slot, appointment_id) " + "VALUES (?, ?, ?, ?);");

            SELECT_NEXT_APPOINTMENT = session.prepare("SELECT * FROM Appointments WHERE specialty = ? ORDER BY priority DESC, timestamp ASC LIMIT 1;");

            SELECT_DOCTOR_BY_SPECIALTY = session.prepare("SELECT doctor_id, start_hours, end_hours FROM Doctors WHERE specialty = ?;");

            CHECK_DOCTOR_AVAILABILITY = session.prepare("SELECT * FROM DoctorAppointments WHERE doctor_id = ? AND appointment_date = ? AND time_slot = ?;");

            INSERT_DOCTOR = session.prepare("INSERT INTO Doctors (doctor_id, name, specialty, start_hours, end_hours) " + "VALUES (?, ?, ?, ?, ?);");

            SELECT_LATEST_DOCTOR_APPOINTMENT = session.prepare("SELECT * FROM DoctorAppointments WHERE doctor_id = ? ORDER BY appointment_date DESC, time_slot DESC LIMIT 1;");

            DELETE_APPOINTMENT = session.prepare("DELETE FROM Appointments WHERE specialty = ? AND priority = ? AND timestamp = ? AND appointment_id = ?;");

        } catch (Exception e) {
            throw new BackendException("Could not prepare statements. " + e.getMessage(), e);
        }
    }

    public void addDoctor(int doctorId, String name, String specialty, String startHours, String endHours) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_DOCTOR);
        bs.bind(doctorId, name, specialty, startHours, endHours);

        try {
            session.execute(bs);
            logger.info("Doctor added: " + name + " (" + specialty + ")");
        } catch (Exception e) {
            throw new BackendException("Could not add doctor. " + e.getMessage(), e);
        }
    }

    public void addAppointment(String specialty, int priority, int appointmentId, String patientFirstName, String patientLastName, Date timestamp) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_APPOINTMENT);
        bs.bind(specialty, priority, appointmentId, patientFirstName, patientLastName, timestamp);

        try {
            session.execute(bs);
            logger.info("Appointment added for " + patientFirstName + " " + patientLastName);
        } catch (Exception e) {
            throw new BackendException("Could not add appointment. " + e.getMessage(), e);
        }
    }

    public void scheduleAppointments() {
        new Thread(() -> {
            while (true) {
                try {
                    processAppointments();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    logger.error("Error scheduling appointments: ", e);
                }
            }
        }).start();
    }

    private void processAppointments() throws BackendException {
        List<String> specialties = Arrays.asList("cardiology", "orthopedics", "general");
        for (String specialty : specialties) {
            scheduleForSpecialty(specialty);
        }
    }

    private void scheduleForSpecialty(String specialty) throws BackendException {
        BoundStatement selectAppointment = new BoundStatement(SELECT_NEXT_APPOINTMENT);
        selectAppointment.bind(specialty);

        ResultSet rs = session.execute(selectAppointment);
        if (rs.isExhausted()) {
            return;
        }

        Row appointmentRow = rs.one();
        int appointmentId = appointmentRow.getInt("appointment_id");
        int priority = appointmentRow.getInt("priority");
        Date timestamp = appointmentRow.getTimestamp("timestamp");
        logger.info("Now processing appointment with id " + appointmentId + ", specialty " + specialty);
        findAvailableDoctor(specialty, appointmentId);
        deleteAppointment(specialty, priority, timestamp, appointmentId);
    }

    private void findAvailableDoctor(String specialty, int appointmentId) throws BackendException {
        BoundStatement selectDoctor = new BoundStatement(SELECT_DOCTOR_BY_SPECIALTY);
        selectDoctor.bind(specialty);

        ResultSet rs = session.execute(selectDoctor);

        LocalDateTime bestAvailableSlot = null;
        int bestDoctorId = -1;

        for (Row row : rs) {
            int doctorId = row.getInt("doctor_id");
            LocalTime startHours = Time.valueOf(row.getString("start_hours")).toLocalTime();
            LocalTime endHours = Time.valueOf(row.getString("end_hours")).toLocalTime();
            LocalDateTime firstAvailableSlot = LocalDate.now().plusDays(1).atTime(startHours);

            BoundStatement selectLatest = new BoundStatement(SELECT_LATEST_DOCTOR_APPOINTMENT);
            selectLatest.bind(doctorId);
            ResultSet result = session.execute(selectLatest);

            if (!result.isExhausted()) {
                Row resultSlot = result.one();
                com.datastax.driver.core.LocalDate slotDate = resultSlot.getDate("appointment_date");
                LocalDate slotLocalDate = LocalDate.of(slotDate.getYear(), slotDate.getMonth(), slotDate.getDay());
                Time slotTime = new Time(resultSlot.getTime("time_slot") / 1000000);
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
        scheduleDoctorAppointment(bestDoctorId, appointmentId, bestAvailableSlot);
    }

    private void scheduleDoctorAppointment(int doctorId, int appointmentId, LocalDateTime timestamp) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_DOCTOR_APPOINTMENT);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(timestamp.getYear(), timestamp.getMonthValue(), timestamp.getDayOfMonth());
        bs.bind(doctorId, cassandraDate, timestamp.toLocalTime().toNanoOfDay(), appointmentId);

        try {
            session.execute(bs);
            logger.info("Doctor appointment for doctor " + doctorId + " sheduled on " + timestamp);
        } catch (Exception e) {
            throw new BackendException("Could not schedule doctor appointment. " + e.getMessage(), e);
        }
    }

    private void deleteAppointment(String specialty, int priority, Date timestamp, int appointmentId) throws BackendException {
        BoundStatement bs = new BoundStatement(DELETE_APPOINTMENT);
        bs.bind(specialty, priority, timestamp, appointmentId);

        try {
            session.execute(bs);
        } catch (Exception e) {
            throw new BackendException("Could not delete appointment. " + e.getMessage(), e);
        }
    }

    @Override
    protected void finalize() {
        try {
            if (session != null) {
                session.getCluster().close();
            }
        } catch (Exception e) {
            logger.error("Could not close resources", e);
        }
    }
}
