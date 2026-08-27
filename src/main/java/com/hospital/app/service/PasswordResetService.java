package com.hospital.app.service;

import com.hospital.app.entity.User;
import com.hospital.app.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepository,
                                OtpService otpService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    // Returns true if an OTP was sent; false if no verified account exists.
    // Only VERIFIED accounts can reset — unverified ones should just re-register.
    @Transactional
    public boolean requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.getIsVerified()) {
            return false;
        }
        otpService.generateAndSend(email, OtpService.OtpPurpose.PASSWORD_RESET);
        return true;
    }

    @Transactional
    public OtpService.OtpResult resetPassword(String email, String code, String newPassword) {
        // Validate password BEFORE checking the OTP — otherwise a too-short
        // password would burn the code and force the user to request a new one.
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        OtpService.OtpResult result = otpService.validate(email, code);

        if (result == OtpService.OtpResult.VALID) {
            userRepository.findByEmail(email).ifPresent(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);
            });
        }
        return result;
    }
}