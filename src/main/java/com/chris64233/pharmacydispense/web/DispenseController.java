package com.chris64233.pharmacydispense.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.pharmacydispense.service.DispenseQueryService;
import com.chris64233.pharmacydispense.service.DispenseService;
import com.chris64233.pharmacydispense.service.DispenseView;
import com.chris64233.pharmacydispense.service.ReturnQueryService;
import com.chris64233.pharmacydispense.service.ReturnView;

@RestController
@RequestMapping("/api")
public class DispenseController {

    private final DispenseService dispenseService;
    private final ReturnQueryService returnQueries;
    private final DispenseQueryService dispenseQueries;

    public DispenseController(DispenseService dispenseService,
                              ReturnQueryService returnQueries,
                              DispenseQueryService dispenseQueries) {
        this.dispenseService = dispenseService;
        this.returnQueries = returnQueries;
        this.dispenseQueries = dispenseQueries;
    }

    /** 提交调剂（businessNo 幂等）。 */
    @PostMapping("/dispenses")
    public ResponseEntity<DispenseView> dispense(@Valid @RequestBody DispenseRequest req) {
        var outcome = dispenseService.dispense(
                req.businessNo(), req.prescriptionNo(), req.quantity());
        // 新建返回 201；幂等重放返回 200
        HttpStatus status = outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(dispenseQueries.getDispense(outcome.record().getBusinessNo()));
    }

    /** 单次调剂的批次扣减明细。 */
    @GetMapping("/dispenses/{businessNo}")
    public DispenseView getDispense(@PathVariable String businessNo) {
        return dispenseQueries.getDispense(businessNo);
    }

    /** 处方的全部调剂（调剂批次查询）。 */
    @GetMapping("/prescriptions/{prescriptionNo}/dispenses")
    public List<DispenseView> listDispenses(@PathVariable String prescriptionNo) {
        return dispenseQueries.listDispenses(prescriptionNo);
    }

    /** 处方的退药链概览（全部退药记录）。 */
    @GetMapping("/prescriptions/{prescriptionNo}/returns")
    public List<ReturnView> listReturns(@PathVariable String prescriptionNo) {
        return returnQueries.listReturnsOfPrescription(prescriptionNo);
    }

    /** 一次调剂的退药链：已退/可退及退药批次明细。 */
    @GetMapping("/dispenses/{businessNo}/returns")
    public ReturnView.Chain returnChain(@PathVariable String businessNo) {
        return returnQueries.returnChain(businessNo);
    }
}
