package cassdemo.scheduling;

import cassdemo.backend.BackendException;
import cassdemo.backend.ClinicBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import static cassdemo.Main.specialties;
import static cassdemo.util.Util.generateUUID;

public class AppointmentGeneratorThread extends Thread {
    private final ClinicBackend clinicBackend;
    private static final Logger logger = LoggerFactory.getLogger(ClinicBackend.class);

    public AppointmentGeneratorThread(ClinicBackend clinicBackend) {
        this.clinicBackend = clinicBackend;
    }

    @Override
    public void run() {
        while (true) {
            int specialtyIndex = ThreadLocalRandom.current().nextInt(specialties.length);
            String specialty = specialties[specialtyIndex];
            int prioritySeed = ThreadLocalRandom.current().nextInt(10);
            int priority = 2;
            if (prioritySeed == 9) {
                priority = 1;
            } else if (prioritySeed > 5) {
                priority = 3;
            }

            try {
                clinicBackend.addAppointment(specialty, priority, generateUUID(), "Jane", "Doe", new Date());
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                logger.error("Interruption error when generating appointments: " + e.getMessage());
            } catch (BackendException e) {
                logger.error("Backend exception when generating appointments: " + e.getMessage());
            }
        }
    }
}
