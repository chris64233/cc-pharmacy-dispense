package com.chris64233.pharmacydispense.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReturnRequest(
        @NotBlank String businessNo,
        @NotBlank String dispenseBusinessNo,
        @NotNull @Min(1) Long quantity) {
}
