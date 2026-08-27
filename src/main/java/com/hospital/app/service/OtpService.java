package com.hospital.app.service;

import com.hospital.app.entity.OtpCode;
import com.hospital.app.repository.OtpCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OtpService {
    public enum OtpPurpose {
        REGISTRATION, PASSWORD_RESET
    }

    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 5; // set to 0 temporarily to test expiry

    // Tells the caller exactly WHY validation failed — better than a plain boolean
    public enum OtpResult {
        VALID, NOT_FOUND, EXPIRED, INVALID_CODE
    }

    private final OtpCodeRepository otpCodeRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(OtpCodeRepository otpCodeRepository, EmailService emailService) {
        this.otpCodeRepository = otpCodeRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void generateAndSend(String email, OtpPurpose purpose) {
        // 1. Invalidate all previously issued, still-unused OTPs for this email
        List<OtpCode> oldCodes = otpCodeRepository.findAllByEmailAndUsedFalse(email);
        oldCodes.forEach(code -> code.setUsed(true));
        otpCodeRepository.saveAll(oldCodes);

        // 2. Generate a random 6-digit code, zero-padded (e.g. "004913")
        String code = String.format("%0" + OTP_LENGTH + "d",
                secureRandom.nextInt((int) Math.pow(10, OTP_LENGTH)));

        // 3. Save it with an expiry timestamp
        OtpCode otp = new OtpCode();
        otp.setEmail(email);
        otp.setCode(code);
        otp.setExpiryAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        otp.setUsed(false);
        otpCodeRepository.save(otp);

        // 4. Send it
        if (purpose == OtpPurpose.PASSWORD_RESET) {
            emailService.sendPasswordResetEmail(email, code);
        } else {
            emailService.sendOtpEmail(email, code);
        }
    }

    @Transactional
    public OtpResult validate(String email, String code) {
        OtpCode otp = otpCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElse(null);

        if (otp == null)
            return OtpResult.NOT_FOUND; // no active code
        if (otp.getExpiryAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true); // clean up expired
            otpCodeRepository.save(otp);
            return OtpResult.EXPIRED;
        }
        if (!otp.getCode().equals(code))
            return OtpResult.INVALID_CODE;

        otp.setUsed(true); // single-use: consume it
        otpCodeRepository.save(otp);
        return OtpResult.VALID;
    }
}