package com.hospital.app.service;

import com.hospital.app.entity.*;
import com.hospital.app.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            DoctorScheduleRepository scheduleRepository,
            AppointmentRepository appointmentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Creating a doctor = TWO tables in one transaction:
    // a login User (DOCTOR role) + a Doctor professional profile.
    @Transactional
    public Doctor createDoctor(String fullName, String email, String phone, String rawPassword,
            Long departmentId, String specialization, String qualification,
            BigDecimal fee) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.DOCTOR);
        user.setIsVerified(true); // admin-created accounts are trusted immediately
        userRepository.save(user);

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found."));

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setDepartment(dept);
        doctor.setSpecialization(specialization);
        doctor.setQualification(qualification);
        doctor.setConsultationFee(fee);
        return doctorRepository.save(doctor);
    }

    // edits BOTH the professional profile AND the login account.
    // Email change = login username change (uniqueness re-checked).
    @Transactional
    public void updateDoctor(Long doctorId, Long departmentId, String specialization,
            String qualification, BigDecimal fee,
            String fullName, String email, String phone) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found."));

        User user = doctor.getUser();
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        userRepository.save(user);

        doctor.setDepartment(dept);
        doctor.setSpecialization(specialization);
        doctor.setQualification(qualification);
        doctor.setConsultationFee(fee);
        doctorRepository.save(doctor);
    }

    // Deletion is blocked while appointments exist — preserving patient
    // medical history is more important than removing the row.
    @Transactional
    public void deleteDoctor(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));

        if (appointmentRepository.countByDoctorId(doctorId) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete: this doctor has appointments in the system (patient history must be preserved).");
        }

        scheduleRepository.deleteAll(scheduleRepository.findByDoctorId(doctorId));
        doctorRepository.delete(doctor);
        userRepository.delete(doctor.getUser()); // login account goes too
    }

    @Transactional
    public void createDepartment(String name, String description) {
        if (departmentRepository.existsByName(name)) {
            throw new IllegalArgumentException("A department named '" + name + "' already exists.");
        }
        Department d = new Department();
        d.setName(name);
        d.setDescription(description);
        departmentRepository.save(d);
    }

    @Transactional
    public void updateDepartment(Long id, String name, String description) {
        Department d = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found."));
        if (departmentRepository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("A department named '" + name + "' already exists.");
        }
        d.setName(name);
        d.setDescription(description);
        departmentRepository.save(d);
    }

    // Referential-integrity guard: deleting a department that still has doctors
    // would orphan their FK. Block it with a clear message instead of a 500 error.
    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new IllegalArgumentException("Department not found.");
        }
        if (!doctorRepository.findByDepartmentId(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete: doctors are still assigned to this department. Reassign them first.");
        }
        departmentRepository.deleteById(id);
    }

    @Transactional
    public void addSchedule(Long doctorId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
        DoctorSchedule s = new DoctorSchedule();
        s.setDoctor(doctor);
        s.setDayOfWeek(dayOfWeek);
        s.setStartTime(start);
        s.setEndTime(end);
        scheduleRepository.save(s);
    }

    @Transactional
    public void removeSchedule(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("Schedule entry not found.");
        }
        scheduleRepository.deleteById(scheduleId);
    }

    // Admin = hospital authority: NO 2-hour cutoff (contrast with the
    // patient-side rule in AppointmentService — different rules per actor).
    @Transactional
    public void adminCancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));
        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("This appointment can no longer be cancelled.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }
}