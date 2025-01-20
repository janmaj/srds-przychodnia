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
    private static final Logger logger = LoggerFactory.getLogger(AppointmentGeneratorThread.class);
    private static final String[] firstNames = {"James", "John", "Robert", "Michael", "William", "David", "Joseph",
            "Charles", "Thomas", "Daniel", "Mary", "Jennifer", "Linda", "Patricia", "Elizabeth", "Susan", "Jessica",
            "Sarah", "Karen", "Nancy"};
    private static final String[] lastNames = {"Smith", "Johnson", "Williams", "Jones", "Brown", "Davis", "Miller", "Wilson", "Moore", "Taylor"};
    private final ClinicBackend clinicBackend;

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

            int firstNameIndex = ThreadLocalRandom.current().nextInt(firstNames.length);
            int lastNameIndex = ThreadLocalRandom.current().nextInt(lastNames.length);
            String patientFirstName = firstNames[firstNameIndex];
            String patientLastName = lastNames[lastNameIndex];

            try {
                clinicBackend.addAppointment(specialty, priority, generateUUID(), patientFirstName, patientLastName, new Date());
                Thread.sleep(200);
            } catch (InterruptedException e) {
                logger.error("Interruption error when generating appointments: " + e.getMessage());
            } catch (BackendException e) {
                logger.error("Backend exception when generating appointments: " + e.getMessage());
            }
        }
    }
}
