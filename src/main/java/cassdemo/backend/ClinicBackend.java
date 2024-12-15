package cassdemo.backend;

import com.datastax.driver.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Date;

public class ClinicBackend {

    private static final Logger logger = LoggerFactory.getLogger(ClinicBackend.class);

    private static PreparedStatement INSERT_APPOINTMENT;
    private static PreparedStatement INSERT_DOCTOR_APPOINTMENT;
    private static PreparedStatement SELECT_NEXT_APPOINTMENT;
    private static PreparedStatement SELECT_DOCTOR_BY_SPECIALTY;
    private static PreparedStatement INSERT_DOCTOR;
    private static PreparedStatement DELETE_APPOINTMENT;
    private static PreparedStatement SELECT_LATEST_DOCTOR_APPOINTMENT;
    private final Session session;

    // Constructor
    public ClinicBackend(String contactPoint, String keyspace) throws BackendException {
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

    public Row selectNextAppointment(String specialty) {
        BoundStatement selectAppointment = new BoundStatement(SELECT_NEXT_APPOINTMENT);
        selectAppointment.bind(specialty);

        ResultSet rs = session.execute(selectAppointment);
        if (rs.isExhausted()) {
            return null;
        }
        return rs.one();
    }

    public Row selectLatestDoctorAppointment(int doctorId) {
        BoundStatement selectLatest = new BoundStatement(SELECT_LATEST_DOCTOR_APPOINTMENT);
        selectLatest.bind(doctorId);
        ResultSet result = session.execute(selectLatest);
        if (result.isExhausted()) {
            return null;
        }
        return result.one();
    }

    public ResultSet getDoctorsBySpecialty(String specialty) {
        BoundStatement selectDoctor = new BoundStatement(SELECT_DOCTOR_BY_SPECIALTY);
        selectDoctor.bind(specialty);

        return session.execute(selectDoctor);
    }

    public void scheduleDoctorAppointment(int doctorId, int appointmentId, LocalDateTime timestamp) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_DOCTOR_APPOINTMENT);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(timestamp.getYear(), timestamp.getMonthValue(), timestamp.getDayOfMonth());
        bs.bind(doctorId, cassandraDate, timestamp.toLocalTime().toNanoOfDay(), appointmentId);

        try {
            session.execute(bs);
            logger.info("Doctor appointment for doctor " + doctorId + " scheduled on " + timestamp);
        } catch (Exception e) {
            throw new BackendException("Could not schedule doctor appointment. " + e.getMessage(), e);
        }
    }

    public void deleteAppointment(String specialty, int priority, Date timestamp, int appointmentId) throws BackendException {
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
