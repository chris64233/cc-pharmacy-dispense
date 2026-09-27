package com.chris64233.pharmacydispense.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.DispenseLine;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DispenseRecordRepository;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.error.NotFoundException;

@Service
public class DispenseQueryService {

    private final DispenseRecordRepository dispenses;
    private final PrescriptionRepository prescriptions;

    public DispenseQueryService(DispenseRecordRepository dispenses,
                                PrescriptionRepository prescriptions) {
        this.dispenses = dispenses;
        this.prescriptions = prescriptions;
    }

    @Transactional(readOnly = true)
    public PrescriptionBalanceView balance(String prescriptionNo) {
        Prescription p = prescriptions.findByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));
        return new PrescriptionBalanceView(
                p.getPrescriptionNo(), p.getPatientId(), p.getPatientName(),
                p.getDrugCode(), p.getDrugName(),
                p.getTotalQuantity(), p.getDispensedQuantity(), p.remainingQuantity(),
                p.getPerDoseLimit(), p.getAllowedDispenseCount(), p.getDispensedCount(),
                p.getStatus(), p.getValidFrom(), p.getValidUntil(), p.getCreatedAt());
    }

    /** 处方的全部调剂及其批次扣减明细。 */
    @Transactional(readOnly = true)
    public List<DispenseView> listDispenses(String prescriptionNo) {
        Prescription p = prescriptions.findByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));
        return dispenses.findByPrescription_IdOrderByIdAsc(p.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public DispenseRecord findDispenseByBusinessNo(String businessNo) {
        return dispenses.findByBusinessNo(businessNo).orElse(null);
    }

    @Transactional(readOnly = true)
    public DispenseView getDispense(String businessNo) {
        DispenseRecord d = dispenses.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("调剂不存在: " + businessNo));
        return toView(d);
    }

    private DispenseView toView(DispenseRecord d) {
        List<DispenseLineView> lines = d.getLines().stream()
                .map(this::toLineView)
                .toList();
        return new DispenseView(d.getBusinessNo(),
                d.getPrescription().getPrescriptionNo(), d.getQuantity(),
                d.getStatus().name(), d.getCreatedAt(), lines);
    }

    private DispenseLineView toLineView(DispenseLine line) {
        return new DispenseLineView(line.getBatch().getBatchNo(), line.getQuantity());
    }
}
