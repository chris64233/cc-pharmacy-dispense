package com.chris64233.pharmacydispense.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.service.DispenseQueryService;
import com.chris64233.pharmacydispense.service.PrescriptionBalanceView;
import com.chris64233.pharmacydispense.service.PrescriptionService;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptions;
    private final DispenseQueryService dispenseQueries;

    public PrescriptionController(PrescriptionService prescriptions,
                                  DispenseQueryService dispenseQueries) {
        this.prescriptions = prescriptions;
        this.dispenseQueries = dispenseQueries;
    }

    @PostMapping
    public ResponseEntity<PrescriptionBalanceView> create(
            @Valid @RequestBody CreatePrescriptionRequest req) {
        Prescription p = prescriptions.create(req.prescriptionNo(), req.patientId(),
                req.patientName(), req.drugCode(), req.drugName(),
                req.totalQuantity(), req.perDoseLimit(), req.allowedDispenseCount(),
                req.validFrom(), req.validUntil());
        return ResponseEntity.created(URI.create("/api/prescriptions/" + p.getPrescriptionNo()))
                .body(dispenseQueries.balance(p.getPrescriptionNo()));
    }

    /** 处方余额查询。 */
    @GetMapping("/{prescriptionNo}")
    public PrescriptionBalanceView balance(@PathVariable String prescriptionNo) {
        return dispenseQueries.balance(prescriptionNo);
    }

    /** 作废处方。 */
    @PostMapping("/{prescriptionNo}/void")
    public ResponseEntity<Void> voidPrescription(@PathVariable String prescriptionNo) {
        prescriptions.voidPrescription(prescriptionNo);
        return ResponseEntity.noContent().build();
    }
}
