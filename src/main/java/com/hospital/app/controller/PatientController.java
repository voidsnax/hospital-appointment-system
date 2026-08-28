package com.hospital.app.controller;

import com.hospital.app.entity.Appointment;
import com.hospital.app.entity.AppointmentStatus;
import com.hospital.app.entity.Doctor;
import com.hospital.app.entity.DoctorSchedule;
import com.hospital.app.entity.Gender;
import com.hospital.app.entity.PatientProfile;
import com.hospital.app.entity.User;
import com.hospital.app.repository.AppointmentRepository;
import com.hospital.app.repository.DepartmentRepository;
import com.hospital.app.repository.DoctorRepository;
import com.hospital.app.repository.DoctorScheduleRepository;
import com.hospital.app.repository.PatientProfileRepository;
import com.hospital.app.repository.PrescriptionRepository;
import com.hospital.app.repository.UserRepository;
import com.hospital.app.service.AppointmentService;
import com.hospital.app.service.PrescriptionViewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/patient")
public class PatientController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionViewService prescriptionViewService;

    public PatientController(UserRepository userRepository,
            DepartmentRepository departmentRepository,
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            PatientProfileRepository patientProfileRepository,
            AppointmentRepository appointmentRepository,
            AppointmentService appointmentService,
            PrescriptionRepository prescriptionRepository,
            PrescriptionViewService prescriptionViewService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionViewService = prescriptionViewService;
    }

    // Who is logged in right now? (email → User entity)
    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    // ---------------- DASHBOARD ----------------

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("user", user);
        model.addAttribute("totalAppointments", appointmentRepository.countByPatientId(user.getId()));
        model.addAttribute("upcoming", appointmentRepository
                .findByPatientIdAndStatusInAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscStartTimeAsc(
                        user.getId(),
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                        LocalDate.now()));
        model.addAttribute("prescriptions", prescriptionRepository
                .findTop3ByAppointmentPatientIdOrderByCreatedAtDesc(user.getId()));
        return "patient/dashboard";
    }

    // ---------------- BROWSE DOCTORS ----------------

    @GetMapping("/doctors")
    public String doctors(@RequestParam(required = false) Long department, Model model) {
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDepartment", department);
        model.addAttribute("doctors", department != null
                ? doctorRepository.findByDepartmentId(department)
                : doctorRepository.findAll());
        return "patient/doctors";
    }

    // ---------------- BOOKING ----------------

    @SuppressWarnings("null")
    @GetMapping("/book")
    public String book(@RequestParam Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
        model.addAttribute("doctor", doctor);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("maxDate", LocalDate.now().plusDays(AppointmentService.MAX_DAYS_AHEAD));

        // This doctor's weekly availability, ordered Mon→Sun
        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorId(doctor.getId());
        schedules.sort(Comparator.comparing(DoctorSchedule::getDayOfWeek));
        model.addAttribute("schedules", schedules);

        if (date != null) {
            model.addAttribute("date", date);
            model.addAttribute("slots", appointmentService.getAvailableSlots(doctorId, date));
        }
        return "patient/book";
    }

    @PostMapping("/appointments")
    public String bookAppointment(Principal principal,
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String startTime,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes) {
        try {
            LocalTime slot = LocalTime.parse(startTime); // "09:30" → LocalTime
            appointmentService.bookAppointment(currentUser(principal), doctorId, date, slot, reason);
            redirectAttributes.addFlashAttribute("success",
                    "Appointment booked! Awaiting doctor confirmation.");
        } catch (java.time.format.DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid time slot.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patient/appointments";
    }

    // ---------------- MY APPOINTMENTS ----------------

    @GetMapping("/appointments")
    public String myAppointments(Principal principal, Model model) {
        List<Appointment> appts = appointmentRepository
                .findByPatientIdOrderByAppointmentDateDescStartTimeAsc(currentUser(principal).getId());
        model.addAttribute("appointments", appts);

        Map<Long, Long> rxIdMap = new HashMap<>();
        for (Appointment a : appts) {
            prescriptionRepository.findByAppointmentId(a.getId())
                    .ifPresent(rx -> rxIdMap.put(a.getId(), rx.getId()));
        }
        model.addAttribute("rxIdMap", rxIdMap);
        return "patient/appointments";
    }

    @PostMapping("/appointments/cancel")
    public String cancel(Principal principal, @RequestParam Long id,
            RedirectAttributes redirectAttributes) {
        try {
            appointmentService.cancelAppointment(id, currentUser(principal));
            redirectAttributes.addFlashAttribute("success", "Appointment cancelled.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patient/appointments";
    }

    // ---------------- MY PROFILE ----------------

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        User user = currentUser(principal);
        // get-or-create: handles patients registered before this phase existed
        PatientProfile profile = patientProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    PatientProfile p = new PatientProfile();
                    p.setUser(user);
                    return patientProfileRepository.save(p);
                });
        model.addAttribute("profile", profile);
        return "patient/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String emergencyContact,
            RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        PatientProfile profile = patientProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    PatientProfile p = new PatientProfile();
                    p.setUser(user);
                    return p;
                });
        profile.setDateOfBirth(dateOfBirth);
        profile.setGender(gender);
        profile.setBloodGroup(bloodGroup);
        profile.setAddress(address);
        profile.setEmergencyContact(emergencyContact);
        patientProfileRepository.save(profile);
        redirectAttributes.addFlashAttribute("success", "Profile updated.");
        return "redirect:/patient/profile";
    }

    // ---------------- MY PRESCRIPTIONS ----------------

    @GetMapping("/prescriptions")
    public String myPrescriptions(Principal principal, Model model) {
        model.addAttribute("prescriptions", prescriptionRepository
                .findByAppointmentPatientIdOrderByCreatedAtDesc(currentUser(principal).getId()));
        return "patient/prescriptions";
    }

    // ---------------- PRESCRIPTION DETAIL ----------------

    @GetMapping("/prescriptions/{id}")
    public String prescriptionDetail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("rx", prescriptionViewService.getForViewer(id, principal));
        model.addAttribute("viewerRole", "PATIENT");
        return "prescription-detail";
    }
}