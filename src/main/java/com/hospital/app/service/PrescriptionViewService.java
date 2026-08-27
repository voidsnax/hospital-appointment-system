package com.hospital.app.service;

import com.hospital.app.entity.Prescription;
import com.hospital.app.entity.User;
import com.hospital.app.repository.PrescriptionRepository;
import com.hospital.app.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class PrescriptionViewService {

    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;

    public PrescriptionViewService(PrescriptionRepository prescriptionRepository,
                                   UserRepository userRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.userRepository = userRepository;
    }

    public Prescription getForViewer(Long prescriptionId, Principal principal) {
        User viewer = userRepository.findByEmail(principal.getName()).orElseThrow();
        Prescription rx = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found."));

        boolean allowed = switch (viewer.getRole()) {
            case PATIENT -> rx.getAppointment().getPatient().getId().equals(viewer.getId());
            case DOCTOR  -> rx.getAppointment().getDoctor().getUser().getId().equals(viewer.getId());
            case ADMIN   -> true;
        };

        if (!allowed) {
            throw new IllegalArgumentException("Prescription not found.");
        }
        return rx;
    }
}