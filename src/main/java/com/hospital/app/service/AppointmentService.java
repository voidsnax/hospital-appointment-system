package com.hospital.app.service;

import com.hospital.app.entity.Appointment;
import com.hospital.app.entity.AppointmentStatus;
import com.hospital.app.entity.Doctor;
import com.hospital.app.entity.DoctorSchedule;
import com.hospital.app.entity.User;
import com.hospital.app.repository.AppointmentRepository;
import com.hospital.app.repository.DoctorRepository;
import com.hospital.app.repository.DoctorScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    // Business rules as named constants — easy to find, easy to defend in a viva
    public static final int SLOT_DURATION_MINUTES = 30;
    public static final int MAX_DAYS_AHEAD = 30;
    public static final int CANCEL_CUTOFF_HOURS = 2;
    private static final int SAME_DAY_LEAD_HOURS = 1;

    // These statuses "occupy" a slot. CANCELLED frees it up again.
    private static final List<AppointmentStatus> SLOT_BLOCKING_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository,
                              DoctorScheduleRepository scheduleRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * All free 30-min slots for a doctor on a date:
     * schedule slots − already-booked slots − (past slots if date is today)
     */
    public List<LocalTime> getAvailableSlots(Long doctorId, LocalDate date) {
        if (date.isBefore(LocalDate.now())
                || date.isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD))) {
            return List.of();
        }

        List<DoctorSchedule> schedules =
                scheduleRepository.findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek());
        if (schedules.isEmpty()) return List.of();   // doctor doesn't work that day

        @SuppressWarnings("null")
        Set<LocalTime> booked = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusIn(doctorId, date, SLOT_BLOCKING_STATUSES)
                .stream()
                .map(Appointment::getStartTime)
                .collect(Collectors.toSet());

        List<LocalTime> slots = new ArrayList<>();
        LocalDateTime leadTime = LocalDateTime.now().plusHours(SAME_DAY_LEAD_HOURS);

        for (DoctorSchedule schedule : schedules) {
            for (LocalTime t = schedule.getStartTime();
                 t.plusMinutes(SLOT_DURATION_MINUTES).compareTo(schedule.getEndTime()) <= 0;
                 t = t.plusMinutes(SLOT_DURATION_MINUTES)) {

                // same-day bookings must be at least 1 hour away
                if (date.equals(LocalDate.now()) && !LocalDateTime.of(date, t).isAfter(leadTime)) {
                    continue;
                }
                if (!booked.contains(t)) {
                    slots.add(t);
                }
            }
        }
        return slots;
    }

    @Transactional
    public Appointment bookAppointment(User patient, Long doctorId, LocalDate date,
                                       LocalTime slot, String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));

        // Re-check INSIDE the transaction: the page may have loaded minutes ago
        if (!getAvailableSlots(doctorId, date).contains(slot)) {
            throw new IllegalArgumentException("That slot is no longer available. Please choose another.");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(date);
        appointment.setStartTime(slot);
        appointment.setEndTime(slot.plusMinutes(SLOT_DURATION_MINUTES));
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setReason(reason);
        appointment.setFee(doctor.getConsultationFee());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, User patient) {
        // findByIdAndPatientId = ownership check built into the query —
        // patient A can NEVER cancel patient B's appointment by guessing IDs
        Appointment appointment = appointmentRepository
                .findByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("This appointment can no longer be cancelled.");
        }

        LocalDateTime slotStart = LocalDateTime.of(appointment.getAppointmentDate(),
                                                   appointment.getStartTime());
        if (!slotStart.isAfter(LocalDateTime.now().plusHours(CANCEL_CUTOFF_HOURS))) {
            throw new IllegalArgumentException("Appointments can only be cancelled more than "
                    + CANCEL_CUTOFF_HOURS + " hours before the scheduled time.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }

        // ---- Doctor actions ----

    // The appointment state machine. Key = current status,
    // Value = the statuses you're allowed to move to.
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            AppointmentStatus.PENDING,   EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED),
            AppointmentStatus.CONFIRMED, EnumSet.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
            AppointmentStatus.COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
            AppointmentStatus.CANCELLED, EnumSet.noneOf(AppointmentStatus.class));

    @Transactional
    public void doctorUpdateStatus(Long appointmentId, Doctor doctor, AppointmentStatus newStatus) {
        // Ownership enforced IN THE QUERY — doctor A can never touch doctor B's appointment
        Appointment appointment = appointmentRepository
                .findByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        if (!ALLOWED_TRANSITIONS.get(appointment.getStatus()).contains(newStatus)) {
            throw new IllegalArgumentException("Cannot change appointment from "
                    + appointment.getStatus() + " to " + newStatus + ".");
        }

        appointment.setStatus(newStatus);
        appointmentRepository.save(appointment);
    }
}