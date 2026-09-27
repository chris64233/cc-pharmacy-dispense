package com.chris64233.pharmacydispense.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.pharmacydispense.service.ReturnQueryService;
import com.chris64233.pharmacydispense.service.ReturnService;
import com.chris64233.pharmacydispense.service.ReturnView;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService returnService;
    private final ReturnQueryService returnQueries;

    public ReturnController(ReturnService returnService, ReturnQueryService returnQueries) {
        this.returnService = returnService;
        this.returnQueries = returnQueries;
    }

    /** 退药（businessNo 幂等），只能针对已完成调剂，退药量不超过原调剂量。 */
    @PostMapping
    public ResponseEntity<ReturnView.Chain> returnMedicine(
            @Valid @RequestBody ReturnRequest req) {
        var outcome = returnService.returnMedicine(
                req.businessNo(), req.dispenseBusinessNo(), req.quantity());
        return ResponseEntity.created(URI.create("/api/returns/"
                        + outcome.record().getBusinessNo()))
                .body(returnQueries.returnChain(req.dispenseBusinessNo()));
    }
}
