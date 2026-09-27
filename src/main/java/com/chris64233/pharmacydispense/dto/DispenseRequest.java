package com.chris64233.pharmacydispense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DispenseRequest(
        @NotBlank String bizNo,
        @Positive long quantity) {
}
