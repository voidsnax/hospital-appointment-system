package com.hospital.app.repository;

import com.hospital.app.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id); // uniqueness check when EDITING
}