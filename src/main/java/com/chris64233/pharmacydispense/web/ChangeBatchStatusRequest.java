package com.chris64233.pharmacydispense.web;

import com.chris64233.pharmacydispense.domain.BatchStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeBatchStatusRequest(@NotNull BatchStatus status) {
}
