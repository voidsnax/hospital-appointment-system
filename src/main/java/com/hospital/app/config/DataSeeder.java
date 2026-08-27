package com.hospital.app.config;

import com.hospital.app.entity.*;
import com.hospital.app.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DataSeeder {

    @Value("${app.admin.email:admin@hospital.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner seedData(UserRepository userRepository,
            DepartmentRepository departmentRepository,
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            PasswordEncoder encoder) {
        return args -> {

            // ---------- 1. Users ----------
            ensureUser(userRepository, encoder, adminEmail, "System Administrator",
                    "9000000001", adminPassword, Role.ADMIN);
            ensureUser(userRepository, encoder, "patient@hospital.com", "Test Patient", "9000000003", "Patient@123",
                    Role.PATIENT);

            User u1 = ensureUser(userRepository, encoder, "doctor@hospital.com", "Dr. Ravi Menon", "9000000010",
                    "Doctor@123", Role.DOCTOR);
            User u2 = ensureUser(userRepository, encoder, "dr.ananya@hospital.com", "Dr. Ananya Rao", "9000000011",
                    "Doctor@123", Role.DOCTOR);
            User u3 = ensureUser(userRepository, encoder, "dr.sarah@hospital.com", "Dr. Sarah Thomas", "9000000012",
                    "Doctor@123", Role.DOCTOR);
            User u4 = ensureUser(userRepository, encoder, "dr.arjun@hospital.com", "Dr. Arjun Nair", "9000000013",
                    "Doctor@123", Role.DOCTOR);

            // ---------- 2. Departments ----------
            if (departmentRepository.count() == 0) {
                departmentRepository.saveAll(List.of(
                        department("Cardiology", "Heart and blood vessel care"),
                        department("General Medicine", "Primary care and general consultations"),
                        department("Dermatology", "Skin, hair and nail care"),
                        department("Pediatrics", "Health care for children")));
                System.out.println(">>> Seeded 4 departments");
            }
            Department general = departmentRepository.findByName("General Medicine").orElseThrow();
            Department cardio = departmentRepository.findByName("Cardiology").orElseThrow();
            Department derma = departmentRepository.findByName("Dermatology").orElseThrow();
            Department pediat = departmentRepository.findByName("Pediatrics").orElseThrow();

            // ---------- 3. Doctor profiles ----------
            Doctor d1 = ensureDoctor(doctorRepository, u1, general, "General Physician", "MBBS, MD",
                    new BigDecimal("300"));
            Doctor d2 = ensureDoctor(doctorRepository, u2, cardio, "Interventional Cardiology",
                    "MBBS, MD, DM (Cardiology)", new BigDecimal("600"));
            Doctor d3 = ensureDoctor(doctorRepository, u3, derma, "Clinical Dermatology", "MBBS, MD (Dermatology)",
                    new BigDecimal("450"));
            Doctor d4 = ensureDoctor(doctorRepository, u4, pediat, "General Pediatrics", "MBBS, DCH",
                    new BigDecimal("400"));

            // ---------- 4. Weekly schedules (Mon–Fri 09:00–13:00) ----------
            ensureWeeklySchedule(scheduleRepository, d1);
            ensureWeeklySchedule(scheduleRepository, d2);
            ensureWeeklySchedule(scheduleRepository, d3);
            ensureWeeklySchedule(scheduleRepository, d4);

            // Cardiologist also works Saturday 10:00–12:00 (shows multi-schedule support)
            if (scheduleRepository.findByDoctorIdAndDayOfWeek(d2.getId(), DayOfWeek.SATURDAY).isEmpty()) {
                DoctorSchedule sat = new DoctorSchedule();
                sat.setDoctor(d2);
                sat.setDayOfWeek(DayOfWeek.SATURDAY);
                sat.setStartTime(LocalTime.of(10, 0));
                sat.setEndTime(LocalTime.of(12, 0));
                scheduleRepository.save(sat);
            }
        };
    }

    // ---------- helpers (all idempotent: safe on every restart) ----------

    private User ensureUser(UserRepository repo, PasswordEncoder encoder, String email,
            String fullName, String phone, String rawPassword, Role role) {
        return repo.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setFullName(fullName);
            u.setEmail(email);
            u.setPhone(phone);
            u.setPassword(encoder.encode(rawPassword));
            u.setRole(role);
            u.setIsVerified(true);
            repo.save(u);
            System.out.println(">>> Seeded user: " + email + " (" + role + ")");
            return u;
        });
    }

    private Department department(String name, String description) {
        Department d = new Department();
        d.setName(name);
        d.setDescription(description);
        return d;
    }

    private Doctor ensureDoctor(DoctorRepository repo, User user, Department dept,
            String specialization, String qualification, BigDecimal fee) {
        return repo.findByUserId(user.getId()).orElseGet(() -> {
            Doctor d = new Doctor();
            d.setUser(user);
            d.setDepartment(dept);
            d.setSpecialization(specialization);
            d.setQualification(qualification);
            d.setConsultationFee(fee);
            repo.save(d);
            System.out.println(">>> Seeded doctor profile for " + user.getEmail());
            return d;
        });
    }

    private void ensureWeeklySchedule(DoctorScheduleRepository repo, Doctor doctor) {
        if (!repo.findByDoctorId(doctor.getId()).isEmpty())
            return;
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            DoctorSchedule s = new DoctorSchedule();
            s.setDoctor(doctor);
            s.setDayOfWeek(day);
            s.setStartTime(LocalTime.of(9, 0));
            s.setEndTime(LocalTime.of(13, 0));
            repo.save(s);
        }
        System.out.println(">>> Seeded weekly schedule for doctor id " + doctor.getId());
    }
}