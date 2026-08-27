package com.hospital.app.repository;

import com.hospital.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hospital.app.entity.Role;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    long countByRole(Role role);
}