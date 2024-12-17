package cassdemo.entities;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

public class Appointment {
    public String specialty;
    public int priority;
    public Date timestamp;
    public int appointmentId;
    public String patientFirstName;
    public String patientLastName;

    public Appointment(String specialty, int priority, Date timestamp, int appointmentId, String patientFirstName, String patientLastName) {
        this.specialty = specialty;
        this.priority = priority;
        this.timestamp = timestamp;
        this.appointmentId = appointmentId;
        this.patientFirstName = patientFirstName;
        this.patientLastName = patientLastName;
    }
}
