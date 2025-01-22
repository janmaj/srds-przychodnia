package cassdemo.entities;

public class AppointmentOwnership {
    public int appointmentId;
    public int schedulerId;

    public AppointmentOwnership(int appointmentId, int schedulerId) {
        this.appointmentId = appointmentId;
        this.schedulerId = schedulerId;
    }
}
