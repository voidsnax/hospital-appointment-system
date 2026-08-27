package com.hospital.app.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendOtpEmail(String toEmail, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("Hospital System <" + fromAddress + ">");
            helper.setTo(toEmail);
            helper.setSubject("Your Hospital System Verification Code");

            String html = """
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 24px; border: 1px solid #ddd; border-radius: 8px;">
                        <h2 style="color: #0d6efd; margin-top: 0;">🏥 Hospital System</h2>
                        <p>Hello,</p>
                        <p>Use the verification code below to complete your registration:</p>
                        <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; background: #f8f9fa; padding: 16px; border-radius: 8px; margin: 16px 0;">
                            %s
                        </div>
                        <p style="color: #6c757d; font-size: 13px;">This code expires in 5 minutes.
                        If you did not request this, you can ignore this email.</p>
                    </div>
                    """
                    .formatted(otpCode);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("Hospital System <" + fromAddress + ">");
            helper.setTo(toEmail);
            helper.setSubject("Your Hospital System password reset code");

            String html = """
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 24px; border: 1px solid #ddd; border-radius: 8px;">
                        <h2 style="color: #0d6efd; margin-top: 0;">🏥 Hospital System</h2>
                        <p>Hello,</p>
                        <p>Use the code below to reset your password:</p>
                        <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; background: #f8f9fa; padding: 16px; border-radius: 8px; margin: 16px 0;">
                            %s
                        </div>
                        <p style="color: #6c757d; font-size: 13px;">This code expires in 5 minutes and can be used once.
                        If you did not request a password reset, you can ignore this email — your password remains unchanged.</p>
                    </div>
                    """
                    .formatted(otpCode);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }
}