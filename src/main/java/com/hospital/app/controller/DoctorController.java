package com.hospital.app.controller;

import com.hospital.app.entity.Appointment;
import com.hospital.app.entity.AppointmentStatus;
import com.hospital.app.entity.Doctor;
import com.hospital.app.entity.Prescription;
import com.hospital.app.entity.User;
import com.hospital.app.repository.AppointmentRepository;
import com.hospital.app.repository.DoctorRepository;
import com.hospital.app.repository.PrescriptionRepository;
import com.hospital.app.repository.UserRepository;
import com.hospital.app.service.AppointmentService;
import com.hospital.app.service.PrescriptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hospital.app.service.PrescriptionViewService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final PrescriptionViewService prescriptionViewService;

    public DoctorController(UserRepository userRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            PrescriptionRepository prescriptionRepository,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            PrescriptionViewService prescriptionViewService) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
        this.prescriptionViewService = prescriptionViewService;
    }

    // Resolve the logged-in user → their doctor profile
    private Doctor currentDoctor(Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No doctor profile linked to this account."));
    }

    // appointmentId → does a prescription exist? (used by templates for the
    // buttons)
    // Note: one query per appointment (N+1). Fine at this scale; production would
    // batch it.
    private Map<Long, Long> prescriptionMap(List<Appointment> appointments) {
        Map<Long, Long> map = new HashMap<>();
        for (Appointment a : appointments) {
            prescriptionRepository.findByAppointmentId(a.getId())
                    .ifPresent(rx -> map.put(a.getId(), rx.getId()));
        }
        return map;
    }

    // ---------------- DASHBOARD ----------------

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        Doctor doctor = currentDoctor(principal);
        LocalDate today = LocalDate.now();

        List<Appointment> pending = appointmentRepository
                .findByDoctorIdAndStatusOrderByAppointmentDateAscStartTimeAsc(
                        doctor.getId(), AppointmentStatus.PENDING);
        List<Appointment> todays = appointmentRepository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctor.getId(), today);

        model.addAttribute("doctor", doctor);
        model.addAttribute("today", today);
        model.addAttribute("pending", pending);
        model.addAttribute("todays", todays);
        model.addAttribute("todayCount", todays.size());
        model.addAttribute("pendingCount", pending.size());
        model.addAttribute("completedTodayCount", appointmentRepository
                .countByDoctorIdAndStatusAndAppointmentDate(
                        doctor.getId(), AppointmentStatus.COMPLETED, today));
        model.addAttribute("rxMap", prescriptionMap(todays));
        return "doctor/dashboard";
    }

    // ---------------- ALL APPOINTMENTS ----------------

    @GetMapping("/appointments")
    public String appointments(Principal principal, Model model) {
        Doctor doctor = currentDoctor(principal);
        List<Appointment> all = appointmentRepository
                .findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(doctor.getId());
        model.addAttribute("appointments", all);
        model.addAttribute("rxMap", prescriptionMap(all));
        return "doctor/appointments";
    }

    // ---------------- STATUS ACTIONS ----------------

    @PostMapping("/appointments/confirm")
    public String confirm(Principal principal, @RequestParam Long id, RedirectAttributes ra) {
        return updateStatus(principal, id, AppointmentStatus.CONFIRMED,
                "Appointment confirmed.", ra);
    }

    @PostMapping("/appointments/reject")
    public String reject(Principal principal, @RequestParam Long id, RedirectAttributes ra) {
        return updateStatus(principal, id, AppointmentStatus.CANCELLED,
                "Appointment rejected.", ra);
    }

    // Completing a consultation jumps STRAIGHT to the prescription form
    @PostMapping("/appointments/complete")
    public String complete(Principal principal, @RequestParam Long id, RedirectAttributes ra) {
        try {
            appointmentService.doctorUpdateStatus(id, currentDoctor(principal), AppointmentStatus.COMPLETED);
            return "redirect:/doctor/prescribe?appointmentId=" + id;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/doctor/appointments";
        }
    }

    private String updateStatus(Principal principal, Long id, AppointmentStatus newStatus,
            String successMessage, RedirectAttributes ra) {
        try {
            appointmentService.doctorUpdateStatus(id, currentDoctor(principal), newStatus);
            ra.addFlashAttribute("success", successMessage);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/appointments";
    }

    // ---------------- PRESCRIBE ----------------

    @GetMapping("/prescribe")
    public String prescribeForm(Principal principal, @RequestParam Long appointmentId, Model model) {
        Doctor doctor = currentDoctor(principal);
        Appointment appointment = appointmentRepository
                .findByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        // Only completed consultations without an existing prescription
        if (appointment.getStatus() != AppointmentStatus.COMPLETED
                || prescriptionRepository.findByAppointmentId(appointmentId).isPresent()) {
            return "redirect:/doctor/appointments";
        }

        model.addAttribute("appointment", appointment);
        return "doctor/prescribe";
    }

    @PostMapping("/prescribe")
    public String savePrescription(Principal principal,
            @RequestParam Long appointmentId,
            @RequestParam String diagnosis,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<String> medicineName,
            @RequestParam(required = false) List<String> dosage,
            @RequestParam(required = false) List<String> duration,
            @RequestParam(required = false) List<String> instructions,
            RedirectAttributes ra) {
        try {
            prescriptionService.writePrescription(currentDoctor(principal), appointmentId,
                    diagnosis, notes, medicineName, dosage, duration, instructions);
            ra.addFlashAttribute("success", "Prescription saved.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/appointments";
    }

    @GetMapping("/prescriptions/{id}")
    public String prescriptionDetail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("rx", prescriptionViewService.getForViewer(id, principal));
        model.addAttribute("viewerRole", "DOCTOR");
        return "prescription-detail";
    }

    // ---------------- EDIT PRESCRIPTION ----------------

    @GetMapping("/prescribe/edit")
    public String editPrescriptionForm(Principal principal,
            @RequestParam Long prescriptionId, Model model) {
        // getForViewer enforces: only the doctor who wrote this prescription gets it
        Prescription rx = prescriptionViewService.getForViewer(prescriptionId, principal);
        model.addAttribute("prescription", rx);
        model.addAttribute("appointment", rx.getAppointment());
        return "doctor/prescribe"; // same template, now in edit mode
    }

    @PostMapping("/prescribe/edit")
    public String saveEditedPrescription(Principal principal,
            @RequestParam Long prescriptionId,
            @RequestParam String diagnosis,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<String> medicineName,
            @RequestParam(required = false) List<String> dosage,
            @RequestParam(required = false) List<String> duration,
            @RequestParam(required = false) List<String> instructions,
            RedirectAttributes ra) {
        try {
            prescriptionService.updatePrescription(currentDoctor(principal), prescriptionId,
                    diagnosis, notes, medicineName, dosage, duration, instructions);
            ra.addFlashAttribute("success", "Prescription updated.");
            return "redirect:/doctor/prescriptions/" + prescriptionId;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/doctor/prescribe/edit?prescriptionId=" + prescriptionId;
        }
    }
}