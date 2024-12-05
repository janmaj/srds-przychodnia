CREATE KEYSPACE IF NOT EXISTS Clinic
  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };

USE Clinic;

CREATE TABLE Appointments (
    specialty varchar,
    priority int,
    appointment_id int,
    patient_first_name varchar,
    patient_last_name varchar,
    timestamp timestamp,
    PRIMARY KEY (specialty, priority, timestamp)
);

CREATE TABLE DoctorAppointments (
    doctor_id int,
    appointment_date date,
    time_slot time,
    appointment_id int,
    PRIMARY KEY (doctor_id, appointment_date, time_slot)
);

CREATE TABLE Doctors (
    doctor_id int,
    specialty varchar,
    first_name varchar,
    last_name varchar,
    start_hours time,
    end_hours time,
    PRIMARY KEY (specialty, start_hours, end_hours)
);