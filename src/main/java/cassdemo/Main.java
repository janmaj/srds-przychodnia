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

public class Main {

    private static final String PROPERTIES_FILENAME = "config.properties";
    public static final String[] specialties = {"cardiology", "orthopedics", "general"};
    private static final int THREADS_PER_SPECIALTY = 1;
    private static final int GENERATOR_THREADS = 1;

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
}