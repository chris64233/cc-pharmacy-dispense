package com.chris64233.pharmacydispense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateBatchRequest(
        @NotBlank String drugCode,
        @NotBlank String batchNo,
        @Positive long quantity,
        @NotNull LocalDate expiryDate) {
}
