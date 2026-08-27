package com.hospital.app.repository;

import com.hospital.app.entity.Appointment;
import com.hospital.app.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

        // Patient's full history (newest first)
        List<Appointment> findByPatientIdOrderByAppointmentDateDescStartTimeAsc(Long patientId);

        // Slots already taken for a doctor on a date
        List<Appointment> findByDoctorIdAndAppointmentDateAndStatusIn(
                        Long doctorId, LocalDate date, List<AppointmentStatus> statuses);

        // Ownership check: only the patient who owns it can cancel
        Optional<Appointment> findByIdAndPatientId(Long id, Long patientId);

        // Dashboard stats
        long countByPatientId(Long patientId);

        // Upcoming appointments for the dashboard
        List<Appointment> findByPatientIdAndStatusInAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscStartTimeAsc(
                        Long patientId, List<AppointmentStatus> statuses, LocalDate date);

        // ---- Doctor module methods ----
        long countByDoctorId(Long doctorId);

        // All pending requests for a doctor (oldest first)
        List<Appointment> findByDoctorIdAndStatusOrderByAppointmentDateAscStartTimeAsc(
                        Long doctorId, AppointmentStatus status);

        // One day's schedule
        List<Appointment> findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(
                        Long doctorId, LocalDate date);

        // Full history (newest first)
        List<Appointment> findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(Long doctorId);

        // Ownership check — doctor can only touch THEIR appointments
        Optional<Appointment> findByIdAndDoctorId(Long id, Long doctorId);

        // Stats
        long countByDoctorIdAndStatus(Long doctorId, AppointmentStatus status);

        long countByDoctorIdAndStatusAndAppointmentDate(Long doctorId, AppointmentStatus status, LocalDate date);

        // ---- Admin module ----

        List<Appointment> findAllByOrderByAppointmentDateDescStartTimeDesc();

        List<Appointment> findByStatusOrderByAppointmentDateDescStartTimeDesc(AppointmentStatus status);

        long countByAppointmentDate(LocalDate date);

        long countByStatus(AppointmentStatus status);

        // Your FIRST hand-written queries — JPQL (Java Persistence Query Language).
        // Reads like property navigation: appointment → doctor → department → name.
        // Spring translates this into SQL with JOINs. Returns [label, count] pairs.
        @Query("SELECT a.doctor.department.name, COUNT(a) FROM Appointment a " +
                        "GROUP BY a.doctor.department.name ORDER BY COUNT(a) DESC")
        List<Object[]> countByDepartment();

        @Query("SELECT a.status, COUNT(a) FROM Appointment a GROUP BY a.status")
        List<Object[]> countByStatusGrouped();
}