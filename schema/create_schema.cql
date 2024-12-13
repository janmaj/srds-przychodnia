
CREATE KEYSPACE IF NOT EXISTS Clinic
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };


USE Clinic;


CREATE TABLE Appointments (
    specialty text,
    priority int,
    timestamp timestamp,
    appointment_id int,
    patient_first_name text,
    patient_last_name text,
    PRIMARY KEY (specialty, priority, timestamp)
) WITH CLUSTERING ORDER BY (priority DESC, timestamp ASC);


CREATE TABLE DoctorAppointments (
    doctor_id int,                
    appointment_date date,        
    time_slot time,               
    appointment_id int,           
    PRIMARY KEY ((doctor_id), appointment_date, time_slot) 
);

CREATE TABLE Doctors (
    doctor_id int,                
    name text,                    
    specialty text,                
    start_hours text,              
    end_hours text,               
    PRIMARY KEY (specialty, doctor_id) 
);