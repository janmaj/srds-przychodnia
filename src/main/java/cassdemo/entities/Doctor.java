package cassdemo.entities;

import java.time.LocalTime;

public class Doctor {
    public int doctorId;
    public String name;
    public String specialty;
    public LocalTime startHours;
    public LocalTime endHours;

    public Doctor(int doctorId, String name, String specialty, LocalTime startHours, LocalTime endHours) {
        this.doctorId = doctorId;
        this.name = name;
        this.specialty = specialty;
        this.startHours = startHours;
        this.endHours = endHours;
    }
}
