package cassdemo;

import cassdemo.backend.BackendException;
import cassdemo.backend.MedicalScheduler;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

    private static final String PROPERTIES_FILENAME = "config.properties";

    public static void main(String[] args) throws IOException, BackendException {
        String contactPoint = null;
        String keyspace = null;

        Properties properties = new Properties();
        try {
            properties.load(Main.class.getClassLoader().getResourceAsStream(PROPERTIES_FILENAME));

            contactPoint = properties.getProperty("contact_point");
            keyspace = properties.getProperty("keyspace");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        MedicalScheduler scheduler = new MedicalScheduler(contactPoint, keyspace);


        System.out.println("Simulating doctor setup...");
        scheduler.addDoctor(UUID.randomUUID().toString(), "Dr. Smith", "general", "08:00:00", "16:00:00");
        scheduler.addDoctor(UUID.randomUUID().toString(), "Dr. Johnson", "general", "10:00:00", "18:00:00");


        System.out.println("Simulating patient requests...");
        String dateString = "13.12.2024 15:00";
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        try{
        Date date = sdf.parse(dateString);
        scheduler.addAppointment("general", 1, 1001, "John", "Doe", date);
        }catch (ParseException e) {
            System.out.println("Error parsing date: " + e.getMessage());
        }
 
        String dateString2 = "13.12.2024 14:00";
        try{
        Date date2 = sdf.parse(dateString2);
        scheduler.addAppointment("general", 2, 1002, "Jane", "Roe", date2);
        }catch (ParseException e) {
            System.out.println("Error parsing date: " + e.getMessage());
        }



        System.out.println("Starting scheduling process...");
        //nie działa jeszcze
        scheduler.scheduleAppointments();


        // try {
        //     Thread.sleep(5000);
        // } catch (InterruptedException e) {
        //     e.printStackTrace();
        // }

        System.exit(0);
    }
}