package cassdemo.entities;

import java.time.LocalDate;
import java.time.LocalTime;

public class DoctorAppointment {
    public int doctorId;
    public LocalDate appointmentDate;
    public LocalTime timeSlot;
    public int appointmentId;
    public int priority;

    public DoctorAppointment(int doctorId, LocalDate appointmentDate, LocalTime timeSlot, int appointmentId, int priority) {
        this.doctorId = doctorId;
        this.appointmentDate = appointmentDate;
        this.timeSlot = timeSlot;
        this.appointmentId = appointmentId;
        this.priority = priority;
    }
}
