package com.hospital.app.service;

import com.hospital.app.entity.Role;
import com.hospital.app.entity.User;
import com.hospital.app.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository,
            OtpService otpService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String fullName, String email, String phone, String rawPassword) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent() && existing.get().getIsVerified()) {
            throw new IllegalArgumentException("This email is already registered.");
        }

        // Reuse the pending (unverified) row, or create a fresh one
        User user = existing.orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.PATIENT);
        user.setIsVerified(false);
        userRepository.save(user);

        otpService.generateAndSend(email, OtpService.OtpPurpose.REGISTRATION);
    }

    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No registration found for this email."));

        if (user.getIsVerified()) {
            throw new IllegalArgumentException("This account is already verified. Please log in.");
        }
        otpService.generateAndSend(email, OtpService.OtpPurpose.REGISTRATION);
    }

    @Transactional
    public OtpService.OtpResult verifyOtp(String email, String code) {
        OtpService.OtpResult result = otpService.validate(email, code);

        if (result == OtpService.OtpResult.VALID) {
            userRepository.findByEmail(email).ifPresent(user -> {
                user.setIsVerified(true);
                userRepository.save(user);
            });
        }
        return result;
    }

}