package com.hospital.app.controller;

import com.hospital.app.entity.AppointmentStatus;
import com.hospital.app.entity.Doctor;
import com.hospital.app.entity.Role;
import com.hospital.app.entity.User;
import com.hospital.app.repository.*;
import com.hospital.app.service.AdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientProfileRepository profileRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AdminService adminService;

    public AdminController(UserRepository userRepository,
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            DoctorScheduleRepository scheduleRepository,
            PatientProfileRepository profileRepository,
            AppointmentRepository appointmentRepository,
            PrescriptionRepository prescriptionRepository,
            AdminService adminService) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.profileRepository = profileRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.adminService = adminService;
    }

    // ---------------- DASHBOARD + REPORTS ----------------

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("patientCount", userRepository.countByRole(Role.PATIENT));
        model.addAttribute("doctorCount", doctorRepository.count());
        model.addAttribute("departmentCount", departmentRepository.count());
        model.addAttribute("todayAppointments", appointmentRepository.countByAppointmentDate(LocalDate.now()));
        model.addAttribute("pendingCount", appointmentRepository.countByStatus(AppointmentStatus.PENDING));
        model.addAttribute("byDepartment", appointmentRepository.countByDepartment());
        model.addAttribute("byStatus", appointmentRepository.countByStatusGrouped());
        return "admin/dashboard";
    }

    // ---------------- DOCTORS ----------------

    @GetMapping("/doctors")
    public String doctors(Model model) {
        model.addAttribute("doctors", doctorRepository.findAll());
        return "admin/doctors";
    }

    @GetMapping("/doctors/add")
    public String addDoctorForm(Model model) {
        model.addAttribute("doctor", null); // null = "add mode" for the template
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("formAction", "/admin/doctors/add");
        return "admin/doctor-form";
    }

    @PostMapping("/doctors/add")
    public String addDoctor(@RequestParam String fullName, @RequestParam String email,
            @RequestParam String phone, @RequestParam String password,
            @RequestParam Long departmentId, @RequestParam String specialization,
            @RequestParam String qualification, @RequestParam BigDecimal fee,
            RedirectAttributes ra) {
        try {
            adminService.createDoctor(fullName, email, phone, password, departmentId,
                    specialization, qualification, fee);
            ra.addFlashAttribute("success", "Doctor added: " + fullName);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/doctors";
    }

    @GetMapping("/doctors/edit")
    public String editDoctorForm(@RequestParam Long id, Model model) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
        model.addAttribute("doctor", doctor); // non-null = "edit mode"
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("formAction", "/admin/doctors/edit");
        return "admin/doctor-form";
    }

    @PostMapping("/doctors/edit")
    public String editDoctor(@RequestParam Long id, @RequestParam Long departmentId,
            @RequestParam String specialization, @RequestParam String qualification,
            @RequestParam BigDecimal fee,
            @RequestParam String fullName, @RequestParam String email,
            @RequestParam String phone,
            RedirectAttributes ra) {
        try {
            adminService.updateDoctor(id, departmentId, specialization, qualification, fee,
                    fullName, email, phone);
            ra.addFlashAttribute("success", "Doctor updated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/doctors";
    }

    @PostMapping("/doctors/delete")
    public String deleteDoctor(@RequestParam Long id, RedirectAttributes ra) {
        try {
            adminService.deleteDoctor(id);
            ra.addFlashAttribute("success", "Doctor deleted.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/doctors";
    }

    // ---------------- DEPARTMENTS ----------------

    @GetMapping("/departments")
    public String departments(Model model) {
        model.addAttribute("departments", departmentRepository.findAll());
        return "admin/departments";
    }

    @PostMapping("/departments/add")
    public String addDepartment(@RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {
        try {
            adminService.createDepartment(name, description);
            ra.addFlashAttribute("success", "Department added.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/edit")
    public String editDepartment(@RequestParam Long id, @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {
        try {
            adminService.updateDepartment(id, name, description);
            ra.addFlashAttribute("success", "Department updated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/delete")
    public String deleteDepartment(@RequestParam Long id, RedirectAttributes ra) {
        try {
            adminService.deleteDepartment(id);
            ra.addFlashAttribute("success", "Department deleted.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    // ---------------- SCHEDULES ----------------

    @GetMapping("/schedules")
    public String schedules(@RequestParam Long doctorId, Model model) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
        model.addAttribute("doctor", doctor);
        model.addAttribute("schedules", scheduleRepository.findByDoctorId(doctorId));
        return "admin/schedules";
    }

    @PostMapping("/schedules/add")
    public String addSchedule(@RequestParam Long doctorId, @RequestParam DayOfWeek dayOfWeek,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            RedirectAttributes ra) {
        try {
            adminService.addSchedule(doctorId, dayOfWeek, startTime, endTime);
            ra.addFlashAttribute("success", "Schedule added.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/schedules?doctorId=" + doctorId;
    }

    @PostMapping("/schedules/delete")
    public String deleteSchedule(@RequestParam Long id, @RequestParam Long doctorId,
            RedirectAttributes ra) {
        adminService.removeSchedule(id);
        ra.addFlashAttribute("success", "Schedule removed.");
        return "redirect:/admin/schedules?doctorId=" + doctorId;
    }

    // ---------------- PATIENTS ----------------

    @GetMapping("/patients")
    public String patients(Model model) {
        model.addAttribute("patients", userRepository.findByRoleOrderByCreatedAtDesc(Role.PATIENT));
        return "admin/patients";
    }

    @GetMapping("/patients/view")
    public String patientView(@RequestParam Long id, Model model) {
        // .filter() → even if someone passes a doctor's id, only PATIENT-role records
        // load
        User patient = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));

        model.addAttribute("patient", patient);
        model.addAttribute("profile", profileRepository.findByUserId(id).orElse(null));
        model.addAttribute("appointments",
                appointmentRepository.findByPatientIdOrderByAppointmentDateDescStartTimeAsc(id));
        model.addAttribute("prescriptions",
                prescriptionRepository.findByAppointmentPatientIdOrderByCreatedAtDesc(id));
        return "admin/patient-view";
    }

    // ---------------- APPOINTMENTS ----------------

    @GetMapping("/appointments")
    public String appointments(@RequestParam(required = false) AppointmentStatus status, Model model) {
        // ?status=PENDING binds the String straight to the enum — Spring converts
        // automatically
        model.addAttribute("appointments", status != null
                ? appointmentRepository.findByStatusOrderByAppointmentDateDescStartTimeDesc(status)
                : appointmentRepository.findAllByOrderByAppointmentDateDescStartTimeDesc());
        model.addAttribute("status", status);
        return "admin/appointments";
    }

    @PostMapping("/appointments/cancel")
    public String cancelAppointment(@RequestParam Long id, RedirectAttributes ra) {
        try {
            adminService.adminCancelAppointment(id);
            ra.addFlashAttribute("success", "Appointment cancelled.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/appointments";
    }
}