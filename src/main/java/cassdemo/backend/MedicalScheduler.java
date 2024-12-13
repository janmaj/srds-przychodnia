package cassdemo.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.*;
import java.util.*;
import java.sql.Time;

public class MedicalScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MedicalScheduler.class);
    private Session session;

    // Prepared statements
    private static PreparedStatement INSERT_APPOINTMENT;
    private static PreparedStatement INSERT_DOCTOR_APPOINTMENT;
    private static PreparedStatement SELECT_NEXT_APPOINTMENT;
    private static PreparedStatement SELECT_DOCTOR_BY_SPECIALTY;
    private static PreparedStatement CHECK_DOCTOR_AVAILABILITY;
    private static PreparedStatement INSERT_DOCTOR;
    private static PreparedStatement DELETE_APPOINTMENT;

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
            INSERT_APPOINTMENT = session.prepare(
                "INSERT INTO Appointments (specialty, priority, appointment_id, patient_first_name, patient_last_name, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?);");

            INSERT_DOCTOR_APPOINTMENT = session.prepare(
                "INSERT INTO DoctorAppointments (doctor_id, appointment_date, time_slot, appointment_id) " +
                "VALUES (?, ?, ?, ?);");

            SELECT_NEXT_APPOINTMENT = session.prepare(
                "SELECT * FROM Appointments WHERE specialty = ? ORDER BY priority DESC, timestamp ASC LIMIT 1;");

            SELECT_DOCTOR_BY_SPECIALTY = session.prepare(
                "SELECT doctor_id, start_hours, end_hours FROM Doctors WHERE specialty = ?;");

            CHECK_DOCTOR_AVAILABILITY = session.prepare(
                "SELECT * FROM DoctorAppointments WHERE doctor_id = ? AND appointment_date = ? AND time_slot = ?;");

            INSERT_DOCTOR = session.prepare(
                "INSERT INTO Doctors (doctor_id, name, specialty, start_hours, end_hours) " +
                "VALUES (?, ?, ?, ?, ?);");

            // DELETE_APPOINTMENT = session.prepare(
            //     "DELETE FROM Appointments WHERE specialty = ? AND appointment_id = ?;");

        } catch (Exception e) {
            throw new BackendException("Could not prepare statements. " + e.getMessage(), e);
        }
    }

    public void addDoctor(String doctorId, String name, String specialty, String startHours, String endHours) throws BackendException {
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
        Row appointmentRow = rs.one();

        if (appointmentRow != null) {
            int appointmentId = appointmentRow.getInt("appointment_id");
            String patientFirstName = appointmentRow.getString("patient_first_name");
            String patientLastName = appointmentRow.getString("patient_last_name");
            Date timestamp = appointmentRow.getTimestamp("timestamp");

            Row doctorRow = findAvailableDoctor(specialty, timestamp);
            if (doctorRow != null) {
                String doctorId = doctorRow.getString("doctor_id");
                scheduleDoctorAppointment(doctorId, appointmentId, timestamp);
                //deleteAppointment(specialty, appointmentId);
                logger.info("Scheduled appointment for patient: " + patientFirstName + " " + patientLastName);
            } else {
                // jesli nie ma dostepnego doktora usunąc appointment i znalezc nową datę todo
                logger.warn("No available doctor for specialty: " + specialty);
            }
        }
    }
    //nie działa jeszcze
    private Row findAvailableDoctor(String specialty, Date timestamp) throws BackendException {
        BoundStatement selectDoctor = new BoundStatement(SELECT_DOCTOR_BY_SPECIALTY);
        selectDoctor.bind(specialty);

        ResultSet rs = session.execute(selectDoctor);

        for (Row row : rs) {
            String doctorId = row.getString("doctor_id");
            Time startHours = Time.valueOf(row.getString("start_hours")); 
            Time endHours = Time.valueOf(row.getString("end_hours"));
            Time timeSlot = new Time(timestamp.getTime());
            
            if (timeSlot.after(startHours) && timeSlot.before(endHours)) {
                BoundStatement checkAvailability = new BoundStatement(CHECK_DOCTOR_AVAILABILITY);
                checkAvailability.bind(doctorId, new java.sql.Date(timestamp.getTime()), timeSlot);

                ResultSet availabilityRs = session.execute(checkAvailability);
                if (availabilityRs.isExhausted()) {
                    return row; 
                }
            }
        }
        return null; 
    }

    private void scheduleDoctorAppointment(String doctorId, int appointmentId, Date timestamp) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_DOCTOR_APPOINTMENT);
        bs.bind(doctorId, new java.sql.Date(timestamp.getTime()), new Time(timestamp.getTime()), appointmentId);

        try {
            session.execute(bs);
        } catch (Exception e) {
            throw new BackendException("Could not schedule doctor appointment. " + e.getMessage(), e);
        }
    }

    private void deleteAppointment(String specialty, int appointmentId) throws BackendException {
        BoundStatement bs = new BoundStatement(DELETE_APPOINTMENT);
        bs.bind(specialty, appointmentId);

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
