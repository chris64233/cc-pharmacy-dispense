package com.chris64233.pharmacydispense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReturnRequest(
        @NotBlank String bizNo,
        @Positive long quantity) {
}
