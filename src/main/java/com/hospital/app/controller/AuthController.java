package com.hospital.app.controller;

import com.hospital.app.dto.RegisterDto;
import com.hospital.app.service.OtpService;
import com.hospital.app.service.RegistrationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hospital.app.service.PasswordResetService;
import com.hospital.app.repository.DoctorRepository;
import com.hospital.app.repository.DepartmentRepository;

import java.util.Random;

@Controller
public class AuthController {

    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final Random random = new Random();

    public AuthController(
            RegistrationService registrationService,
            PasswordResetService passwordResetService,
            DepartmentRepository departmentRepository,
            DoctorRepository doctorRepository) {

        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("doctorCount", doctorRepository.count());
        return "home";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // ---------------- REGISTRATION ----------------

    @GetMapping("/register")
    public String registerPage(Model model, HttpSession session) {
        model.addAttribute("registerDto", new RegisterDto());
        generateCaptcha(model, session);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterDto registerDto,
            BindingResult bindingResult,
            @RequestParam(value = "captchaAnswer", required = false) Integer captchaAnswer,
            HttpSession session,
            Model model) {

        if (bindingResult.hasErrors()) {
            generateCaptcha(model, session);
            return "register";
        }

        Integer expected = (Integer) session.getAttribute("captchaAnswer");
        if (expected == null || !expected.equals(captchaAnswer)) {
            model.addAttribute("error", "Captcha answer is wrong. Please try again.");
            generateCaptcha(model, session);
            return "register";
        }
        session.removeAttribute("captchaAnswer");

        if (!registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            generateCaptcha(model, session);
            return "register";
        }

        try {
            registrationService.register(
                    registerDto.getFullName(),
                    registerDto.getEmail(),
                    registerDto.getPhone(),
                    registerDto.getPassword());
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            generateCaptcha(model, session);
            return "register";
        }

        // NEW: remember the email server-side instead of passing it in the URL
        session.setAttribute("pendingEmail", registerDto.getEmail());
        return "redirect:/verify-otp"; // clean URL
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam(required = false) String error,
            @RequestParam(name = "resent", required = false) String resent,
            HttpSession session,
            Model model) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            // No pending registration in this session (stale link, direct visit) → start
            // over
            return "redirect:/register";
        }
        model.addAttribute("email", email);
        model.addAttribute("error", error);
        model.addAttribute("resent", resent != null);
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(HttpSession session, @RequestParam String code) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            return "redirect:/register";
        }

        OtpService.OtpResult result = registrationService.verifyOtp(email, code);

        return switch (result) {
            case VALID -> {
                session.removeAttribute("pendingEmail");
                yield "redirect:/login?verified";
            }
            case EXPIRED -> "redirect:/verify-otp?error=expired";
            case NOT_FOUND -> "redirect:/verify-otp?error=notfound";
            case INVALID_CODE -> "redirect:/verify-otp?error=invalid";
        };
    }

    @PostMapping("/resend-otp")
    public String resendOtp(HttpSession session, RedirectAttributes ra) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            return "redirect:/register";
        }
        try {
            registrationService.resendOtp(email);
            return "redirect:/verify-otp?resent";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    // ---------------- CAPTCHA ----------------

    private void generateCaptcha(Model model, HttpSession session) {
        int a = random.nextInt(10) + 1; // 1..10
        int b = random.nextInt(10) + 1;
        session.setAttribute("captchaAnswer", a + b); // answer lives server-side only
        model.addAttribute("captchaQuestion", a + " + " + b + " = ?");
    }

    // ---------------- FORGOT PASSWORD ----------------

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam(required = false) String email,
            HttpSession session,
            RedirectAttributes ra) {
        // Resend case: no email in the form → reuse the one remembered in session
        if (email == null || email.isBlank()) {
            email = (String) session.getAttribute("resetEmail");
        }
        if (email == null) {
            return "redirect:/forgot-password";
        }
        if (passwordResetService.requestReset(email)) {
            session.setAttribute("resetEmail", email);
            return "redirect:/reset-password";
        }
        ra.addFlashAttribute("error", "No verified account found with that email.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String error,
            HttpSession session, Model model) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password"; // no active reset flow in this session
        }
        model.addAttribute("email", email);
        model.addAttribute("error", error);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(HttpSession session,
            @RequestParam String code,
            @RequestParam String password,
            @RequestParam String confirmPassword) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password";
        }
        if (!password.equals(confirmPassword)) {
            return "redirect:/reset-password?error=mismatch";
        }
        try {
            OtpService.OtpResult result = passwordResetService.resetPassword(email, code, password);
            return switch (result) {
                case VALID -> {
                    session.removeAttribute("resetEmail");
                    yield "redirect:/login?reset";
                }
                case EXPIRED -> "redirect:/reset-password?error=expired";
                case NOT_FOUND -> "redirect:/reset-password?error=notfound";
                case INVALID_CODE -> "redirect:/reset-password?error=invalid";
            };
        } catch (IllegalArgumentException e) {
            return "redirect:/reset-password?error=generic";
        }
    }
}