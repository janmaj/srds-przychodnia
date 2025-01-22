package cassdemo;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import cassdemo.scheduling.AppointmentGeneratorThread;
import cassdemo.scheduling.AppointmentSchedulerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;

public class Main {

    public static final String[] specialties = {"cardiology", "orthopedics", "general"};
    private static final String PROPERTIES_FILENAME = "config.properties";
    private static Logger logger;
    private static int SCHEDULERS_PER_SPECIALTY = 2;
    private static int GENERATOR_THREADS = 2;
    private static int DOCTOR_COUNT = 4;

    public static void main(String[] args) throws BackendException {
        String contactPoint = null;
        String keyspace = null;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger anomalyCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        Properties properties = new Properties();
        try {
            properties.load(Main.class.getClassLoader().getResourceAsStream(PROPERTIES_FILENAME));

            contactPoint = properties.getProperty("contact_point");
            keyspace = properties.getProperty("keyspace");

            SCHEDULERS_PER_SPECIALTY = Integer.parseInt(properties.getProperty("threads_per_specialty", "2"));
            GENERATOR_THREADS = Integer.parseInt(properties.getProperty("generator_threads", "2"));
            DOCTOR_COUNT = Integer.parseInt(properties.getProperty("doctor_count", "4"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.setProperty("LOG_LEVEL", "warn");

        for (String arg : args) {
            if ("-D".equals(arg)) {
                System.setProperty("LOG_LEVEL", "info");
            }
            if (arg.startsWith("-g")) {
                GENERATOR_THREADS = Integer.parseInt(arg.substring(2));
            }
            if (arg.startsWith("-s")) {
                SCHEDULERS_PER_SPECIALTY = Integer.parseInt(arg.substring(2));
            }
            if (arg.startsWith("-d")) {
                DOCTOR_COUNT = Integer.max(Integer.parseInt(arg.substring(2)), specialties.length);
            }
        }

        logger = LoggerFactory.getLogger(Main.class);

        ClinicBackend backend = new ClinicBackend(contactPoint, keyspace, readCount, writeCount);

        System.out.print("\033[H\033[2J");
        addDoctors(backend, DOCTOR_COUNT);
//         backend.addDoctor(1, "Dr. Smith", "general", "08:00:00", "16:00:00");
//         backend.addDoctor(2, "Dr. Johnson", "general", "10:00:00", "14:00:00");
//         backend.addDoctor(3, "Dr. Williams", "cardiology", "08:00:00", "16:00:00");
//         backend.addDoctor(4, "Dr. Walker", "orthopedics", "08:00:00", "16:00:00");

        if (GENERATOR_THREADS == 0 && SCHEDULERS_PER_SPECIALTY == 0) {
            System.exit(0);
        }
        List<Thread> schedulerThreads = new ArrayList<>();

        for (int i = 0; i < GENERATOR_THREADS; i++) {
            Thread generatorThread = new AppointmentGeneratorThread(backend);
            generatorThread.setDaemon(true);
            generatorThread.start();
        }

        for (String specialty : specialties) {
            for (int i = 0; i < SCHEDULERS_PER_SPECIALTY; i++) {
                Thread schedulerThread = new AppointmentSchedulerThread(backend, specialty, anomalyCount, successCount);
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
            while (true) {
                readCount.set(0);
                writeCount.set(0);
                sleep(2000);
                logger.warn("Reads/second: " + readCount.get() / 2.0);
                logger.warn("Writes/second: " + writeCount.get() / 2.0);
                logger.warn("Total anomaly count: " + anomalyCount.get());
                logger.warn("Total scheduled appointments: " + successCount.get());
                logger.warn("-----");
            }
        } catch (InterruptedException e) {
            logger.info("Application terminated by user");
        }
    }

    private static void addDoctors(ClinicBackend scheduler, int doctorCount) {
        Random random = new Random();
        String[] doctorNames = {
                "Müller", "Schmidt", "Schneider", "Fischer", "Weber",
                "Meyer", "Wagner", "Becker", "Hoffmann", "Schulz",
                "Zimmermann", "Hartmann", "Lange", "Schröder", "Koch",
                "Bauer", "Richter", "Klein", "Wolf", "Neumann"
        };

        for (int i = 1; i <= doctorCount; i++) {
            String specialty = specialties[(i - 1) % specialties.length];
            String doctorName = "Dr. " + doctorNames[random.nextInt(doctorNames.length)];

            int startHour = 6 + random.nextInt(10);
            int endHour = startHour + 4 + random.nextInt(6);
            if (endHour > 22) endHour = 22;

            String startTime = String.format("%02d:00:00", startHour);
            String endTime = String.format("%02d:00:00", endHour);

            try {
                scheduler.addDoctor(i, doctorName, specialty, startTime, endTime);
                logger.info("Added doctor: " + doctorName + ", Specialty: " + specialty +
                        ", Working Hours: " + startTime + " - " + endTime);
            } catch (BackendException e) {
                logger.error("Error adding doctor " + doctorName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}