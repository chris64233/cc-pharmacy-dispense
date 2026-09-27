package com.chris64233.pharmacydispense.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.chris64233.pharmacydispense.domain.PrescriptionStatus;

public record PrescriptionBalanceView(String prescriptionNo,
                                      String patientId,
                                      String patientName,
                                      String drugCode,
                                      String drugName,
                                      long totalQuantity,
                                      long dispensedQuantity,
                                      long remainingQuantity,
                                      long perDoseLimit,
                                      int allowedDispenseCount,
                                      int dispensedCount,
                                      PrescriptionStatus status,
                                      LocalDate validFrom,
                                      LocalDate validUntil,
                                      LocalDateTime createdAt) {
}
