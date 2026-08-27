package com.hospital.app.entity;

public enum AppointmentStatus {
    PENDING,      // booked, waiting for doctor to confirm
    CONFIRMED,    // doctor accepted
    COMPLETED,    // consultation happened
    CANCELLED     // patient or admin cancelled
}