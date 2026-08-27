package com.hospital.app.repository;

import com.hospital.app.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // The newest unused OTP for an email ("Top" + "OrderBy...Desc" = latest first)
    Optional<OtpCode> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    // All unused OTPs for an email — used to invalidate old ones when resending
    List<OtpCode> findAllByEmailAndUsedFalse(String email);
}