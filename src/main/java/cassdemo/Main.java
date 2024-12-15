package cassdemo;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import cassdemo.scheduling.AppointmentGeneratorThread;
import cassdemo.scheduling.AppointmentSchedulerThread;

import java.io.IOException;
import java.util.Properties;
import java.util.TimeZone;

public class Main {

    private static final String PROPERTIES_FILENAME = "config.properties";
    public static final String[] specialties = {"cardiology", "orthopedics", "general"};
    private static final int THREADS_PER_SPECIALTY = 2;
    private static final int GENERATOR_THREADS = 2;

    public static void main(String[] args) throws BackendException {
        String contactPoint = null;
        String keyspace = null;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Properties properties = new Properties();
        try {
            properties.load(Main.class.getClassLoader().getResourceAsStream(PROPERTIES_FILENAME));

            contactPoint = properties.getProperty("contact_point");
            keyspace = properties.getProperty("keyspace");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        ClinicBackend scheduler = new ClinicBackend(contactPoint, keyspace);


        System.out.println("Simulating doctor setup...");
//        scheduler.addDoctor(generateUUID(), "Dr. Smith", "general", "08:00:00", "16:00:00");
//        scheduler.addDoctor(generateUUID(), "Dr. Johnson", "general", "10:00:00", "18:00:00");
        scheduler.addDoctor(1, "Dr. Smith", "general", "08:00:00", "16:00:00");
        scheduler.addDoctor(2, "Dr. Johnson", "general", "10:00:00", "14:00:00");
        scheduler.addDoctor(3, "Dr. Williams", "cardiology", "08:00:00", "16:00:00");
        scheduler.addDoctor(4, "Dr. Walker", "orthopedics", "08:00:00", "16:00:00");

        for (int i = 0; i < GENERATOR_THREADS; i++) {
            new AppointmentGeneratorThread(scheduler).start();
        }

        for (String specialty : specialties) {
            for (int i = 0; i < THREADS_PER_SPECIALTY; i++) {
                new AppointmentSchedulerThread(scheduler, specialty).start();
            }

        }
    }
}