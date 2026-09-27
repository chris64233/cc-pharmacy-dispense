package com.chris64233.pharmacydispense.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispenseRequest(
        @NotBlank String businessNo,
        @NotBlank String prescriptionNo,
        @NotNull @Min(1) Long quantity) {
}
