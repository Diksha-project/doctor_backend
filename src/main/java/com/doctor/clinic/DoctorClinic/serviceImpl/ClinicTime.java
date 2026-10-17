package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Business clock for clinic appointments, independent of the server's UTC timezone. */
public final class ClinicTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private ClinicTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalTime now() {
        return LocalTime.now(ZONE);
    }

    public static LocalDateTime dateTimeNow() {
        return LocalDateTime.now(ZONE);
    }
}
