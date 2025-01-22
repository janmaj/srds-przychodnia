# System Zarządzania Kliniką z użyciem Cassandry

## Autorzy
- **Michał Dropiewski**: 148189  
- **Jan Majchrzak**

## Opis Projektu
Projekt symuluje system harmonogramowania wizyt w klinice, który:
- Zarządza lekarzami.
- Organizuje terminy wizyt pacjentów.
- Rozwiązuje konflikty harmonogramów.

System wykorzystuje **wielowątkowość** do dynamicznego generowania i planowania wizyt, zapewniając wydajne przetwarzanie operacji wykonywanych jednocześnie.

---

## Struktura Systemu

### **Moduły Backendowe**
- **ClinicBackend**: Obsługuje interakcję z bazą danych Cassandra. Zarządza operacjami lekarzy, wizyt i roszczeń własności.
- **BackendException**: Niestandardowy wyjątek do obsługi błędów operacji backendowych.

### **Klasy Domenowe**
- **Doctor**: Reprezentuje szczegóły lekarza, w tym specjalizację i godziny pracy.
- **Appointment**: Przechowuje dane pacjenta, priorytet wizyty i znacznik czasowy.
- **DoctorAppointment**: Łączy lekarzy z zaplanowanymi wizytami.
- **AppointmentOwnership**: Zarządza własnością wizyt w celu rozwiązania konfliktów.

### **Wątki**
- **AppointmentGeneratorThread**: Losowo generuje wizyty i wprowadza je do systemu.
- **AppointmentSchedulerThread**: Przydziela wizyty lekarzom w oparciu o dostępność, specjalizację i priorytet. Obsługuje logikę usuwania wizyt o niższym priorytecie.

---

## Funkcjonalności

### **Zarządzanie Lekarzami**
- Lekarze są dynamicznie dodawani do systemu z losowymi godzinami pracy w przedziale **6:00 - 20:00**.
- Każdy lekarz jest przypisany do jednej z trzech specjalizacji:
  - **Kardiologia**
  - **Ortopedia**
  - **Ogólna**

### **Zarządzanie Wizytami**
- Pacjenci są przypisywani do specjalizacji na podstawie losowego wyboru.
- Priorytety wizyt są ustawiane dynamicznie i wpływają na harmonogramowanie oraz politykę usuwania wizyt.

---

## Plik Konfiguracyjny (`config.properties`)
Plik `config.properties` określa:
- Szczegóły połączenia z bazą Cassandra:
  - **`contact_point`**
  - **`keyspace`**
- Liczbę wątków do generowania i harmonogramowania wizyt.
- Liczbę lekarzy do symulacji (**`doctor_count`**).


---

## Autorzy
- Michał Dropiewski
- Jan Majchrzak







