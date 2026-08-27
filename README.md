# 🏥 Hospital Appointment & Patient Record System

A full-stack web application for managing hospital appointments and patient records, built with Spring Boot. Patients book appointments with doctors,doctors manage their schedules and write prescriptions, and administrators manage doctors, departments, and patient records — with role-based access control throughout.

## Getting Started

### Prerequisites

- JDK 17 or newer

- MySQL 8.x or newer (running locally)

- Maven (or just use the included Maven wrapper ./mvnw)

- A Gmail account with 2-Step Verification enabled, for sending OTP emails


### Clone the repo

```bash
git clone https://github.com/voidsnax/hospital-appointment-system.git
```

### Create the database
Using the MySQL client of your choice (Workbench, DBeaver, VS Code extension,
or the mysql CLI):

```sql
CREATE DATABASE hospital_db;
CREATE USER 'hospital_user'@'%' IDENTIFIED BY '<your-db-password>';
GRANT ALL PRIVILEGES ON hospital_db.* TO 'hospital_user'@'%';
FLUSH PRIVILEGES;
```

Configure credentials — create a .env file in the project root:

```text
# should use the same pass when user is created
DB_PASSWORD=<your-db-password>

# Gmail account used to SEND the OTP emails
MAIL_USERNAME=<yourgmail@gmail.com>
MAIL_PASSWORD=<16-char Gmail App Password>

# Optional: override the seeded admin account
# APP_ADMIN_EMAIL=admin@hospital.com
# APP_ADMIN_PASSWORD=<admin-password>
```
*About the Gmail App Password: with 2-Step Verification enabled on the
account, generate one at https://myaccount.google.com/apppasswords.
This is not the account's normal login password.*

### Run

```bash
./mvnw spring-boot:run
```

*On first start Hibernate creates all tables (ddl-auto: update) and the
data seeder creates the admin account, four departments, four demo doctors
with weekly schedules, and one demo patient.*

Open http://localhost:8080

### Demo Accounts

| Role | Email | Password |
| --- | --- | --- |
| Admin | admin@hospital.com | Admin@123 |
| Doctor | doctor@hospital.com | Doctor@123 |
| Patient | patient@hospital.com | Patient@123 |

*Seeded automatically on first start (configurable via .env for admin).
Additional doctors: dr.ananya@…, dr.sarah@…, dr.arjun@… (same password).
Registration and password-reset OTP emails are sent to the address you
enter — use a real inbox when testing those flows.*

## Features

### Patient

- Register with email verification (OTP)

- Login / logout, forgot password (email OTP reset)

- Browse doctors, filter by department

- Book appointments in available time slots

- View appointment history, cancel appointments (>2 hours before slot)

- View and print prescriptions

- Maintain basic profile (DOB, gender, blood group, address, emergency contact)

### Doctor

- Dashboard: today's schedule, pending requests, completed-today stats

- Confirm / reject appointment requests

- Complete consultations and write prescriptions (diagnosis + medicines)

- Edit their own prescriptions

- View appointment history

### Admin

- Dashboard with reports (appointments by department / by status)

- Manage doctors (add / edit / delete), assign login credentials

- Manage departments (add / edit / delete with referential-integrity guards)

- Manage doctor weekly schedules (drives available booking slots)

- Browse patient records: profile, appointment history, prescriptions

- View/cancel any appointment

### Security & Access Control

- Role-based access (PATIENT / DOCTOR / ADMIN) enforced by Spring Security

- BCrypt password hashing

- Email OTP verification for registration and password reset

- Math captcha on registration

- Ownership checks on every record (patients see only their data,doctors only their consultations)

- CSRF protection on all forms

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 3.5 (Spring Web, Security, Data JPA, Validation, Mail) |
| Database | MySQL 8.4 |
| ORM | Hibernate via Spring Data JPA |
| Templates | Thymeleaf |
| UI | Bootstrap 5 |
| Build | Maven |


## Project Set Up

The skeleton was generated with the official [Spring Initializr](https://start.spring.io) (also available inside VS Code via the Spring Boot
Extension Pack):

*You can either generate the project from the website and extract the folder, or in VS Code press `Ctrl+Shift+P`, select **Spring Initializr: Create a Maven Project**, and follow the prompts.*

| Setting | Value |
|---|---|
| Project | Maven · Java · Jar packaging |
| Group / Artifact | `com.hospital` / `hospital-system` (package: `com.hospital.app`) |
| Spring Boot | 4.1.1 |


### Dependencies selected

- Spring Web — controllers + embedded Tomcat
- Spring Data JPA — database access (Hibernate)
- MySQL Driver — JDBC driver for MySQL
- Thymeleaf — server-side HTML templates
- Spring Security — authentication & role-based access
- Validation — bean validation for forms
- Lombok — removes getter/setter boilerplate
- Spring Boot DevTools — hot reload during development
- Java Mail Sender — OTP emails via SMTP


## Project Structure

```text
src/main/java/com/hospital/app/
├── config/        # Security config, data seeders
├── controller/    # Web controllers (auth, patient, doctor, admin)
├── dto/           # Form-binding objects (registration)
├── entity/        # JPA entities (User, Doctor, Appointment, Prescription...)
├── repository/    # Spring Data JPA repositories
├── security/      # Custom UserDetailsService (DB-backed auth)
└── service/       # Business logic (booking, OTP, prescriptions, admin ops)
src/main/resources/
├── templates/     # Thymeleaf views (per role + fragments + error pages)
|── static/        # favicon
└── application.yml
```

## Design Notes

- Appointment state machine: PENDING → CONFIRMED → COMPLETED
(or CANCELLED at PENDING/CONFIRMED); transitions validated server-side.

- Slot availability: 30-minute slots from each doctor's weekly schedule,
minus booked slots, minus past slots (1h lead time for same-day booking).

- Cancellation rules: patients >2 hours before the slot; admin any time;
doctors may reject pending/confirmed appointments.

- Business rules live in the service layer (AppointmentService,
PrescriptionService, OtpService...), not in controllers.

- Emails: OTP verification (registration) and password-reset codes are
sent through Gmail SMTP by a dedicated EmailService.

## Known Limitations (Future Improvements)

- improved or more polished UI

- OTP emails from Gmail SMTP frequently land in recipients' spam folders
(no custom domain / SPF / DKIM). Production would use a transactional
email provider with a verified domain.

- OTP codes are stored unhashed; no per-OTP purpose column (a single
active code per email makes this safe in practice).

- No rate limiting on OTP resend / verification attempts.

- Password-reset mails to non-existent inboxes silently bounce
(no bounce processing).

- Consultations can be completed on any date (no on/after-date check).

- Payment is modeled as a fee field only; no payment gateway (out of scope).

- Deleting a doctor with appointment history is blocked (to preserve
patient records) — production would soft-delete/deactivate instead.

- No audit logging; no automated tests yet.

