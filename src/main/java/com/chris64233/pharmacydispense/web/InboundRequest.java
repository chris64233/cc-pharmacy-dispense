package com.chris64233.pharmacydispense.web;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InboundRequest(
        @NotBlank String drugCode,
        @NotBlank String batchNo,
        @NotNull @Min(1) Long quantity,
        @NotNull LocalDate expiryDate,
        @NotBlank String inboundNo) {
}
