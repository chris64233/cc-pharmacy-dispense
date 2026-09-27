package com.chris64233.pharmacydispense.web;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePrescriptionRequest(
        @NotBlank String prescriptionNo,
        @NotBlank String patientId,
        @NotBlank String patientName,
        @NotBlank String drugCode,
        @NotBlank String drugName,
        @NotNull @Min(1) Long totalQuantity,
        @NotNull @Min(1) Long perDoseLimit,
        @NotNull @Min(1) Integer allowedDispenseCount,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil) {
}
