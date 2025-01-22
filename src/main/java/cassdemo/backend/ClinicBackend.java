package cassdemo.backend;

import cassdemo.entities.Appointment;
import cassdemo.entities.AppointmentOwnership;
import cassdemo.entities.Doctor;
import cassdemo.entities.DoctorAppointment;
import com.datastax.driver.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClinicBackend {

    private static final Logger logger = LoggerFactory.getLogger(ClinicBackend.class);

    private static PreparedStatement INSERT_APPOINTMENT;
    private static PreparedStatement INSERT_DOCTOR_APPOINTMENT;
    private static PreparedStatement UPDATE_DOCTOR_APPOINTMENT;
    private static PreparedStatement SELECT_DOCTOR_APPOINTMENTS;
    private static PreparedStatement SELECT_PENDING_APPOINTMENTS;
    private static PreparedStatement SELECT_DOCTOR_BY_SPECIALTY;
    private static PreparedStatement INSERT_DOCTOR;
    private static PreparedStatement DELETE_APPOINTMENT;
    private static PreparedStatement SELECT_LATEST_DOCTOR_APPOINTMENT;
    private static PreparedStatement SELECT_DOCTOR_SLOT;
    private static PreparedStatement UPSERT_OWNERSHIP;
    private static PreparedStatement SELECT_OWNERSHIP;
    private static PreparedStatement DELETE_OWNERSHIP;
    private final Session session;

    private final AtomicInteger readCount;
    private final AtomicInteger writeCount;

    // Constructor
    public ClinicBackend(String contactPoint, String keyspace, AtomicInteger readCount, AtomicInteger writeCount) throws BackendException {
        Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
        try {
            session = cluster.connect(keyspace);

        } catch (Exception e) {
            throw new BackendException("Could not connect to the cluster. " + e.getMessage(), e);
        }
        prepareStatements();
        this.readCount = readCount;
        this.writeCount = writeCount;
    }

    private void prepareStatements() throws BackendException {
        try {
            INSERT_APPOINTMENT = session.prepare("INSERT INTO Appointments (specialty, priority, appointment_id, patient_first_name, patient_last_name, timestamp) " + "VALUES (?, ?, ?, ?, ?, ?);");

            INSERT_DOCTOR_APPOINTMENT = session.prepare("INSERT INTO DoctorAppointments (doctor_id, appointment_date, time_slot, appointment_id, priority, patient_first_name, patient_last_name) " + "VALUES (?, ?, ?, ?, ?, ?, ?);");

            SELECT_PENDING_APPOINTMENTS = session.prepare("SELECT * FROM Appointments WHERE specialty = ? ORDER BY priority DESC, timestamp ASC LIMIT 50;");

            SELECT_DOCTOR_BY_SPECIALTY = session.prepare("SELECT doctor_id, name, start_hours, end_hours FROM Doctors WHERE specialty = ?;");

            INSERT_DOCTOR = session.prepare("INSERT INTO Doctors (doctor_id, name, specialty, start_hours, end_hours) " + "VALUES (?, ?, ?, ?, ?);");

            SELECT_LATEST_DOCTOR_APPOINTMENT = session.prepare("SELECT * FROM DoctorAppointments WHERE doctor_id = ? ORDER BY appointment_date DESC, time_slot DESC LIMIT 1;");

            SELECT_DOCTOR_APPOINTMENTS = session.prepare("SELECT * FROM DoctorAppointments WHERE doctor_id = ? AND appointment_date = ? ORDER BY time_slot ASC;");

            UPDATE_DOCTOR_APPOINTMENT = session.prepare("UPDATE DoctorAppointments SET appointment_id = ?, priority = ?, patient_first_name = ?, patient_last_name = ? WHERE doctor_id = ? AND appointment_date = ? AND time_slot = ?;");

            UPSERT_OWNERSHIP = session.prepare("INSERT INTO AppointmentOwnership (appointment_id, scheduler_id) VALUES (?, ?);");

            SELECT_OWNERSHIP = session.prepare("SELECT * FROM AppointmentOwnership WHERE appointment_id = ?;");

            DELETE_OWNERSHIP = session.prepare("DELETE FROM AppointmentOwnership WHERE appointment_id = ?;");

            SELECT_DOCTOR_SLOT = session.prepare("SELECT * FROM DoctorAppointments WHERE doctor_id = ? AND appointment_date = ? AND time_slot = ?;");

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
            writeCount.getAndIncrement();
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
            writeCount.getAndIncrement();
        } catch (Exception e) {
            throw new BackendException("Could not add appointment. " + e.getMessage(), e);
        }
    }

    public List<Appointment> selectPendingAppointments(String specialty) {
        BoundStatement bs = new BoundStatement(SELECT_PENDING_APPOINTMENTS);
        bs.bind(specialty);
        ResultSet rs = session.execute(bs);
        readCount.getAndIncrement();
        List<Appointment> appointments = new ArrayList<>();
        for (Row row : rs) {
            int priority = row.getInt("priority");
            Date timestamp = row.getTimestamp("timestamp");
            int appointmentId = row.getInt("appointment_id");
            String patientFirstName = row.getString("patient_first_name");
            String patientLastName = row.getString("patient_last_name");

            appointments.add(new Appointment(specialty, priority, timestamp, appointmentId, patientFirstName, patientLastName));
        }
        return appointments;
    }

    public void claimAppointmentOwnership(int appointmentId, int schedulerId) {
        BoundStatement bs = new BoundStatement(UPSERT_OWNERSHIP);
        bs.bind(appointmentId, schedulerId);
        session.execute(bs);
        writeCount.getAndIncrement();
    }

    public AppointmentOwnership selectOwnership(int appointmentId) {
        BoundStatement bs = new BoundStatement(SELECT_OWNERSHIP);
        bs.bind(appointmentId);
        ResultSet rs = session.execute(bs);
        readCount.getAndIncrement();
        if (rs.isExhausted()) return null;
        Row row = rs.one();
        int schedulerId = row.getInt("scheduler_id");
        return new AppointmentOwnership(appointmentId, schedulerId);
    }

    public void deleteOwnership(int appointmentId) {
        BoundStatement bs = new BoundStatement(DELETE_OWNERSHIP);
        bs.bind(appointmentId);
        session.execute(bs);
        writeCount.getAndIncrement();
    }

    public DoctorAppointment selectLatestDoctorAppointment(int doctorId) {
        BoundStatement selectLatest = new BoundStatement(SELECT_LATEST_DOCTOR_APPOINTMENT);
        selectLatest.bind(doctorId);
        ResultSet rs = session.execute(selectLatest);
        readCount.getAndIncrement();
        if (rs.isExhausted()) {
            return null;
        }
        Row row = rs.one();
        Time slotTime = new Time(row.getTime("time_slot") / 1000000);
        LocalTime timeSlot = slotTime.toLocalTime();
        com.datastax.driver.core.LocalDate slotDate = row.getDate("appointment_date");
        LocalDate appointmentDate = LocalDate.of(slotDate.getYear(), slotDate.getMonth(), slotDate.getDay());
        int appointmentId = row.getInt("appointment_id");
        int priority = row.getInt("priority");
        String patientName = row.getString("patient_first_name");
        String patientLastName = row.getString("patient_last_name");

        return new DoctorAppointment(doctorId, appointmentDate, timeSlot, appointmentId, priority, patientName, patientLastName);
    }

    public List<Doctor> getDoctorsBySpecialty(String specialty) {
        BoundStatement selectDoctor = new BoundStatement(SELECT_DOCTOR_BY_SPECIALTY);
        selectDoctor.bind(specialty);
        List<Doctor> doctors = new ArrayList<>();
        ResultSet rs = session.execute(selectDoctor);
        readCount.getAndIncrement();
        for (Row row : rs) {
            int doctorId = row.getInt("doctor_id");
            LocalTime startHours = Time.valueOf(row.getString("start_hours")).toLocalTime();
            LocalTime endHours = Time.valueOf(row.getString("end_hours")).toLocalTime();
            String name = row.getString("name");
            doctors.add(new Doctor(doctorId, name, specialty, startHours, endHours));
        }
        return doctors;
    }

    public DoctorAppointment checkScheduleSlot(int doctorId, LocalDate appointmentDate, LocalTime timeSlot) {
        BoundStatement bs = new BoundStatement(SELECT_DOCTOR_SLOT);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(appointmentDate.getYear(), appointmentDate.getMonthValue(), appointmentDate.getDayOfMonth());
        bs.bind(doctorId, cassandraDate, timeSlot.toNanoOfDay());
        ResultSet rs = session.execute(bs);
        readCount.getAndIncrement();
        if (rs.isExhausted()) return null;
        Row row = rs.one();
        int appointmentId = row.getInt("appointment_id");
        int priority = row.getInt("priority");
        String patientName = row.getString("patient_first_name");
        String patientLastName = row.getString("patient_last_name");

        return new DoctorAppointment(doctorId, appointmentDate, timeSlot, appointmentId, priority, patientName, patientLastName);
    }

    public void scheduleDoctorAppointment(int doctorId, int appointmentId, LocalDateTime timestamp, int priority, String patientName, String patientLastName) throws BackendException {
        BoundStatement bs = new BoundStatement(INSERT_DOCTOR_APPOINTMENT);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(timestamp.getYear(), timestamp.getMonthValue(), timestamp.getDayOfMonth());
        bs.bind(doctorId, cassandraDate, timestamp.toLocalTime().toNanoOfDay(), appointmentId, priority, patientName, patientLastName);

        try {
            session.execute(bs);
            writeCount.getAndIncrement();
            logger.info("Doctor appointment for doctor " + doctorId + " scheduled on " + timestamp);
        } catch (Exception e) {
            throw new BackendException("Could not schedule doctor appointment. " + e.getMessage(), e);
        }
    }

    public List<DoctorAppointment> getDoctorDaySchedule(int doctorId, LocalDate appointmentDate) {
        BoundStatement bs = new BoundStatement(SELECT_DOCTOR_APPOINTMENTS);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(appointmentDate.getYear(), appointmentDate.getMonthValue(), appointmentDate.getDayOfMonth());
        bs.bind(doctorId, cassandraDate);
        ResultSet rs = session.execute(bs);
        readCount.getAndIncrement();
        List<DoctorAppointment> appointments = new ArrayList<>();
        for (Row row : rs) {
            int appointmentId = row.getInt("appointment_id");
            int priority = row.getInt("priority");
            String patientName = row.getString("patient_first_name");
            String patientLastName = row.getString("patient_last_name");
            LocalTime timeSlot = (new Time(row.getTime("time_slot") / 1000000)).toLocalTime();

            appointments.add(new DoctorAppointment(doctorId, appointmentDate, timeSlot, appointmentId, priority, patientName, patientLastName));
        }
        return appointments;
    }

    public void updateDoctorAppointment(int doctorId, LocalDate appointmentDate, LocalTime timeSlot, int appointmentId, int priority, String patientName, String patientLastName) {
        BoundStatement bs = new BoundStatement(UPDATE_DOCTOR_APPOINTMENT);
        com.datastax.driver.core.LocalDate cassandraDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(appointmentDate.getYear(), appointmentDate.getMonthValue(), appointmentDate.getDayOfMonth());
        bs.bind(appointmentId, priority, patientName, patientLastName, doctorId, cassandraDate, timeSlot.toNanoOfDay());
        session.execute(bs);
        writeCount.getAndIncrement();
    }

    public void deleteAppointment(Appointment a) throws BackendException {
        BoundStatement bs = new BoundStatement(DELETE_APPOINTMENT);
        bs.bind(a.specialty, a.priority, a.timestamp, a.appointmentId);

        try {
            session.execute(bs);
            writeCount.getAndIncrement();
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
