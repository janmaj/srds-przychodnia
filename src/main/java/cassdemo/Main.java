package cassdemo;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import cassdemo.scheduling.AppointmentGeneratorThread;
import cassdemo.scheduling.AppointmentSchedulerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Random;

public class Main {

    private static final String PROPERTIES_FILENAME = "config.properties";
    public static final String[] specialties = {"cardiology", "orthopedics", "general"};
    private static int THREADS_PER_SPECIALTY = 2;
    private static int GENERATOR_THREADS = 2;
    private static int DOCTOR_COUNT = 4;

    public static void main(String[] args) throws BackendException {

//        Signal.handle(new Signal("INT"), signal -> {
//            for(int i = 0; i< 100; i++)
//                System.out.println("Interrupt signal received. Cleaning up...");
//        });

        String contactPoint = null;
        String keyspace = null;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Properties properties = new Properties();
        try {
            properties.load(Main.class.getClassLoader().getResourceAsStream(PROPERTIES_FILENAME));

            contactPoint = properties.getProperty("contact_point");
            keyspace = properties.getProperty("keyspace");

            THREADS_PER_SPECIALTY = Integer.parseInt(properties.getProperty("threads_per_specialty", "2"));
            GENERATOR_THREADS = Integer.parseInt(properties.getProperty("generator_threads", "2"));
            DOCTOR_COUNT = Integer.parseInt(properties.getProperty("doctor_count", "4"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        ClinicBackend scheduler = new ClinicBackend(contactPoint, keyspace);

        System.out.println("Simulating doctor setup...");
        addDoctors(scheduler, DOCTOR_COUNT);
//        scheduler.addDoctor(generateUUID(), "Dr. Smith", "general", "08:00:00", "16:00:00");
//        scheduler.addDoctor(generateUUID(), "Dr. Johnson", "general", "10:00:00", "18:00:00");
        // scheduler.addDoctor(1, "Dr. Smith", "general", "08:00:00", "16:00:00");
        // scheduler.addDoctor(2, "Dr. Johnson", "general", "10:00:00", "14:00:00");
        // scheduler.addDoctor(3, "Dr. Williams", "cardiology", "08:00:00", "16:00:00");
        // scheduler.addDoctor(4, "Dr. Walker", "orthopedics", "08:00:00", "16:00:00");

        List<Thread> schedulerThreads = new ArrayList<>();

        for (int i = 0; i < GENERATOR_THREADS; i++) {
            Thread generatorThread = new AppointmentGeneratorThread(scheduler);
            generatorThread.setDaemon(true);
            generatorThread.start();
        }

        for (String specialty : specialties) {
            for (int i = 0; i < THREADS_PER_SPECIALTY; i++) {
                Thread schedulerThread = new AppointmentSchedulerThread(scheduler, specialty);
                schedulerThreads.add(schedulerThread);
                schedulerThread.start();
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Thread schedulerThread : schedulerThreads) {
                ((AppointmentSchedulerThread) schedulerThread).stopScheduling();
            }
            try {
                for (Thread schedulerThread : schedulerThreads) {
                    schedulerThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        try {
            while(true) Thread.sleep(60000);
        } catch (InterruptedException e) {
            System.out.println("Main thread: handling interruptedexception");
        }
    }

    private static void addDoctors(ClinicBackend scheduler, int doctorCount) {
        String[] specialtyPool = specialties;
        Random random = new Random();

        for (int i = 1; i <= doctorCount; i++) {

            String specialty = specialtyPool[(i - 1) % specialtyPool.length];
            String doctorName = "Dr. " + "Name" + i;


            int startHour = 6 + random.nextInt(15);
            int endHour = startHour + 4 + random.nextInt(6);
            if (endHour > 20) endHour = 20;

            String startTime = String.format("%02d:00:00", startHour);
            String endTime = String.format("%02d:00:00", endHour);

            try {
                scheduler.addDoctor(i, doctorName, specialty, startTime, endTime);
                System.out.println("Added doctor: " + doctorName + ", Specialty: " + specialty +
                                   ", Working Hours: " + startTime + " - " + endTime);
            } catch (BackendException e) {
                System.err.println("Error adding doctor " + doctorName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}