package com.hospital.app.repository;

import com.hospital.app.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByDepartmentId(Long departmentId);

    Optional<Doctor> findByUserId(Long userId);
}