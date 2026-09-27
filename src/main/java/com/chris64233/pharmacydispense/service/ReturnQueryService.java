package com.chris64233.pharmacydispense.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DispenseRecordRepository;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.domain.ReturnLine;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.domain.ReturnRecordRepository;
import com.chris64233.pharmacydispense.error.NotFoundException;

@Service
public class ReturnQueryService {

    private final ReturnRecordRepository returns;
    private final DispenseRecordRepository dispenses;
    private final PrescriptionRepository prescriptions;

    public ReturnQueryService(ReturnRecordRepository returns,
                              DispenseRecordRepository dispenses,
                              PrescriptionRepository prescriptions) {
        this.returns = returns;
        this.dispenses = dispenses;
        this.prescriptions = prescriptions;
    }

    /** 某处方的全部退药记录。 */
    @Transactional(readOnly = true)
    public List<ReturnView> listReturnsOfPrescription(String prescriptionNo) {
        Prescription p = prescriptions.findByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));
        return returns.findByOriginalDispense_Prescription_IdOrderByIdAsc(p.getId()).stream()
                .map(this::toView)
                .toList();
    }

    /** 退药链：一次原调剂下的全部退药记录与可退余额。 */
    @Transactional(readOnly = true)
    public ReturnView.Chain returnChain(String dispenseBusinessNo) {
        DispenseRecord dispense = dispenses.findByBusinessNo(dispenseBusinessNo)
                .orElseThrow(() -> new NotFoundException("调剂不存在: " + dispenseBusinessNo));
        List<ReturnView> views =
                returns.findByOriginalDispense_IdOrderByIdAsc(dispense.getId()).stream()
                        .map(this::toView)
                        .toList();
        long returned = views.stream().mapToLong(ReturnView::quantity).sum();
        return new ReturnView.Chain(dispense.getBusinessNo(), dispense.getQuantity(),
                returned, dispense.getQuantity() - returned, views);
    }

    @Transactional(readOnly = true)
    public ReturnRecord findReturnByBusinessNo(String businessNo) {
        return returns.findByBusinessNo(businessNo).orElse(null);
    }

    private ReturnView toView(ReturnRecord r) {
        List<DispenseLineView> lines = r.getLines().stream()
                .map(this::toLineView)
                .toList();
        return new ReturnView(r.getBusinessNo(),
                r.getOriginalDispense().getBusinessNo(), r.getQuantity(),
                r.getCreatedAt(), lines);
    }

    private DispenseLineView toLineView(ReturnLine line) {
        return new DispenseLineView(line.getBatch().getBatchNo(), line.getQuantity());
    }
}
