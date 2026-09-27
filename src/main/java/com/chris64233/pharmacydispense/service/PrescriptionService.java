package com.chris64233.pharmacydispense.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.error.NotFoundException;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final Clock clock;

    public PrescriptionService(PrescriptionRepository prescriptions, Clock clock) {
        this.prescriptions = prescriptions;
        this.clock = clock;
    }

    @Transactional
    public Prescription create(String prescriptionNo, String patientId, String patientName,
                               String drugCode, String drugName, long totalQuantity,
                               long perDoseLimit, int allowedDispenseCount,
                               LocalDate validFrom, LocalDate validUntil) {
        if (totalQuantity <= 0) {
            throw new BusinessRuleException("处方总剂量必须为正");
        }
        if (perDoseLimit <= 0) {
            throw new BusinessRuleException("单次上限必须为正");
        }
        if (perDoseLimit > totalQuantity) {
            throw new BusinessRuleException("单次上限不能超过总剂量");
        }
        if (allowedDispenseCount <= 0) {
            throw new BusinessRuleException("允许调剂次数必须为正");
        }
        if (validFrom == null || validUntil == null || validUntil.isBefore(validFrom)) {
            throw new BusinessRuleException("处方有效期不合法");
        }
        if (prescriptions.existsByPrescriptionNo(prescriptionNo)) {
            throw new BusinessRuleException("处方号已存在: " + prescriptionNo);
        }
        return prescriptions.save(new Prescription(prescriptionNo, patientId, patientName,
                drugCode, drugName, totalQuantity, perDoseLimit, allowedDispenseCount,
                validFrom, validUntil, LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public Prescription get(String prescriptionNo) {
        return prescriptions.findByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));
    }

    /**
     * 作废处方。处方行悲观写锁与调剂事务互斥：
     * 已产生完整调剂的处方拒绝作废；与并发调剂竞争时只有一方成功，
     * 结果只能是“已作废”或“存在完整调剂”之一。
     */
    @Transactional
    public void voidPrescription(String prescriptionNo) {
        try {
            Prescription p = prescriptions.findForUpdateByPrescriptionNo(prescriptionNo)
                    .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));
            p.voidPrescription();
        } catch (ConcurrencyFailureException e) {
            throw new ConflictException("并发作废冲突，请重试", e);
        }
    }
}
