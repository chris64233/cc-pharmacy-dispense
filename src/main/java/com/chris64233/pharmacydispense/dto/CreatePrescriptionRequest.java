package com.chris64233.pharmacydispense.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreatePrescriptionRequest(
        @NotBlank String patientName,
        @NotBlank String drugCode,
        String drugName,
        @Positive long totalQuantity,
        @Positive long maxPerDispense,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @Min(1) int allowedDispenseCount) {
}
