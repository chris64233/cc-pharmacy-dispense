package com.chris64233.pharmacydispense.dto;

import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionStatus;

import java.time.LocalDate;

public record PrescriptionView(
        Long id,
        String patientName,
        String drugCode,
        String drugName,
        long totalQuantity,
        long dispensedQuantity,
        long remainingQuantity,
        long maxPerDispense,
        LocalDate validFrom,
        LocalDate validTo,
        int allowedDispenseCount,
        int dispensedCount,
        int remainingCount,
        PrescriptionStatus status) {

    public static PrescriptionView from(Prescription p) {
        return new PrescriptionView(p.getId(), p.getPatientName(), p.getDrugCode(), p.getDrugName(),
                p.getTotalQuantity(), p.getDispensedQuantity(), p.remainingQuantity(),
                p.getMaxPerDispense(), p.getValidFrom(), p.getValidTo(),
                p.getAllowedDispenseCount(), p.getDispensedCount(), p.remainingCount(),
                p.getStatus());
    }
}
