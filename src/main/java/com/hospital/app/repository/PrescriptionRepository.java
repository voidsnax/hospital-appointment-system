package com.hospital.app.repository;

import com.hospital.app.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByAppointmentId(Long appointmentId);

    // Nested property traversal! Reads as:
    // "find by prescription→appointment→patient→id" — Spring generates the JOINs
    List<Prescription> findByAppointmentPatientIdOrderByCreatedAtDesc(Long patientId);

    // newest 3 only
    List<Prescription> findTop3ByAppointmentPatientIdOrderByCreatedAtDesc(Long patientId);
}