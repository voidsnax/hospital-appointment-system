package com.hospital.app.service;

import com.hospital.app.entity.Appointment;
import com.hospital.app.entity.AppointmentStatus;
import com.hospital.app.entity.Doctor;
import com.hospital.app.entity.Prescription;
import com.hospital.app.entity.PrescriptionMedicine;
import com.hospital.app.repository.AppointmentRepository;
import com.hospital.app.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
            AppointmentRepository appointmentRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public void writePrescription(Doctor doctor, Long appointmentId, String diagnosis, String notes,
            List<String> medicineNames, List<String> dosages,
            List<String> durations, List<String> instructions) {

        Appointment appointment = appointmentRepository
                .findByIdAndDoctorId(appointmentId, doctor.getId()) // ownership check
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Prescriptions can only be written for completed consultations.");
        }
        if (prescriptionRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new IllegalArgumentException("A prescription already exists for this appointment.");
        }

        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setDiagnosis(diagnosis.trim());
        prescription.setNotes((notes == null || notes.isBlank()) ? null : notes.trim());

        // "Zip" the four parallel lists by index into medicine objects.
        // Rows with an empty medicine name are skipped.
        int count = (medicineNames == null) ? 0 : medicineNames.size();
        for (int i = 0; i < count; i++) {
            @SuppressWarnings("null")
            String name = (medicineNames.get(i) == null) ? "" : medicineNames.get(i).trim();
            if (name.isEmpty())
                continue;

            PrescriptionMedicine medicine = new PrescriptionMedicine();
            medicine.setPrescription(prescription); // side 1 of the link
            medicine.setMedicineName(name);
            medicine.setDosage(valueAt(dosages, i));
            medicine.setDuration(valueAt(durations, i));
            medicine.setInstructions(valueAt(instructions, i));
            prescription.getMedicines().add(medicine); // side 2 of the link
        }

        if (prescription.getMedicines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the prescription.");
        }

        prescriptionRepository.save(prescription); // cascade=ALL saves all medicines too
    }

    // Null-safe list lookup — lists can be null/short if the form submitted oddly
    private String valueAt(List<String> list, int index) {
        if (list == null || index >= list.size())
            return null;
        String value = list.get(index);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

        @Transactional
    public void updatePrescription(Doctor doctor, Long prescriptionId, String diagnosis, String notes,
                                   List<String> medicineNames, List<String> dosages,
                                   List<String> durations, List<String> instructions) {

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found."));

        // Ownership: only the doctor who WROTE it can edit it
        if (!prescription.getAppointment().getDoctor().getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("Prescription not found.");
        }

        if (diagnosis == null || diagnosis.isBlank()) {
            throw new IllegalArgumentException("Diagnosis is required.");
        }

        // Replace the medicine list wholesale: clearing triggers orphanRemoval
        // (old rows deleted), then we add the new ones.
        prescription.getMedicines().clear();

        int count = (medicineNames == null) ? 0 : medicineNames.size();
        for (int i = 0; i < count; i++) {
            @SuppressWarnings("null")
            String name = (medicineNames.get(i) == null) ? "" : medicineNames.get(i).trim();
            if (name.isEmpty()) continue;

            PrescriptionMedicine medicine = new PrescriptionMedicine();
            medicine.setPrescription(prescription);
            medicine.setMedicineName(name);
            medicine.setDosage(valueAt(dosages, i));
            medicine.setDuration(valueAt(durations, i));
            medicine.setInstructions(valueAt(instructions, i));
            prescription.getMedicines().add(medicine);
        }

        if (prescription.getMedicines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the prescription.");
        }

        prescription.setDiagnosis(diagnosis.trim());
        prescription.setNotes((notes == null || notes.isBlank()) ? null : notes.trim());
        prescriptionRepository.save(prescription);
    }
}