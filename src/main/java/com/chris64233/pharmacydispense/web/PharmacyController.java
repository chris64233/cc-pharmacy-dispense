package com.chris64233.pharmacydispense.web;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.dto.BatchView;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import com.chris64233.pharmacydispense.dto.DispenseRequest;
import com.chris64233.pharmacydispense.dto.DispenseView;
import com.chris64233.pharmacydispense.dto.PrescriptionView;
import com.chris64233.pharmacydispense.dto.ReturnRequest;
import com.chris64233.pharmacydispense.dto.ReturnView;
import com.chris64233.pharmacydispense.service.PharmacyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PharmacyController {

    private final PharmacyService service;

    public PharmacyController(PharmacyService service) {
        this.service = service;
    }

    // ---------- 建档 / 库存 ----------

    @PostMapping("/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionView createPrescription(@Valid @RequestBody CreatePrescriptionRequest req) {
        return PrescriptionView.from(service.createPrescription(req));
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView createBatch(@Valid @RequestBody CreateBatchRequest req) {
        return BatchView.from(service.createBatch(req));
    }

    /** 修改批次状态：NORMAL / FROZEN / RECALLED（冻结、召回批次不可调剂）。 */
    @PatchMapping("/batches/{id}/status")
    public BatchView changeBatchStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        BatchStatus newStatus = BatchStatus.valueOf(body.get("status"));
        return BatchView.from(service.changeBatchStatus(id, newStatus));
    }

    // ---------- 调剂 / 退药 / 作废 ----------

    @PostMapping("/prescriptions/{id}/dispenses")
    @ResponseStatus(HttpStatus.CREATED)
    public DispenseView dispense(@PathVariable Long id, @Valid @RequestBody DispenseRequest req) {
        return DispenseView.from(service.dispense(id, req.bizNo(), req.quantity()));
    }

    @PostMapping("/dispenses/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnView returnDispense(@PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        return ReturnView.from(service.returnDispense(id, req.bizNo(), req.quantity()));
    }

    @PostMapping("/prescriptions/{id}/cancel")
    public PrescriptionView cancel(@PathVariable Long id) {
        return PrescriptionView.from(service.cancelPrescription(id));
    }

    // ---------- 查询：余额 / 调剂批次 / 退药链 / 台账 ----------

    /** 处方余额 */
    @GetMapping("/prescriptions/{id}")
    public PrescriptionView getPrescription(@PathVariable Long id) {
        return PrescriptionView.from(service.getPrescription(id));
    }

    /** 处方的全部调剂（含每次扣减的批次明细） */
    @GetMapping("/prescriptions/{id}/dispenses")
    public List<DispenseView> listDispenses(@PathVariable Long id) {
        return service.listDispenses(id).stream().map(DispenseView::from).toList();
    }

    /** 退药链：一张调剂记录对应的全部退药及批次回补明细 */
    @GetMapping("/dispenses/{id}/returns")
    public List<ReturnView> listReturns(@PathVariable Long id) {
        return service.listReturns(id).stream().map(ReturnView::from).toList();
    }

    /** 库存台账（可按药品过滤） */
    @GetMapping("/batches")
    public List<BatchView> ledger(@RequestParam(required = false) String drugCode) {
        return service.ledger(drugCode).stream().map(BatchView::from).toList();
    }
}
