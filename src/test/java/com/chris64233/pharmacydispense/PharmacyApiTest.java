package com.chris64233.pharmacydispense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTP 接口与错误码映射的端到端测试。
 */
class PharmacyApiTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mvc;
    }

    @Test
    void fullFlowViaHttp() throws Exception {
        String rxJson = """
                {"prescriptionNo":"RX-W1","patientId":"P1","patientName":"张三",
                 "drugCode":"DRUG-W","drugName":"药品W","totalQuantity":100,
                 "perDoseLimit":60,"allowedDispenseCount":3,
                 "validFrom":"%s","validUntil":"%s"}
                """.formatted(TODAY.minusDays(1), TODAY.plusDays(30));
        mvc().perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(rxJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingQuantity").value(100))
                .andExpect(jsonPath("$.dispensedCount").value(0));

        mvc().perform(post("/api/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"drugCode":"DRUG-W","batchNo":"WB1","quantity":30,
                                 "expiryDate":"%s","inboundNo":"WIN1"}
                                """.formatted(TODAY.plusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc().perform(post("/api/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"drugCode":"DRUG-W","batchNo":"WB2","quantity":40,
                                 "expiryDate":"%s","inboundNo":"WIN2"}
                                """.formatted(TODAY.plusDays(8))))
                .andExpect(status().isCreated());

        // 调剂 50：FEFO 跨两批
        mvc().perform(post("/api/dispenses")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WD1","prescriptionNo":"RX-W1","quantity":50}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].batchNo").value("WB1"))
                .andExpect(jsonPath("$.lines[0].quantity").value(30))
                .andExpect(jsonPath("$.lines[1].batchNo").value("WB2"))
                .andExpect(jsonPath("$.lines[1].quantity").value(20));

        // 幂等重放返回 200
        mvc().perform(post("/api/dispenses")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WD1","prescriptionNo":"RX-W1","quantity":50}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2));

        // 余额与调剂批次查询
        mvc().perform(get("/api/prescriptions/RX-W1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(50))
                .andExpect(jsonPath("$.dispensedCount").value(1));
        mvc().perform(get("/api/prescriptions/RX-W1/dispenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessNo").value("WD1"));

        // 退药 20 并查退药链
        mvc().perform(post("/api/returns")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WR1","dispenseBusinessNo":"WD1","quantity":20}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnedQuantity").value(20))
                .andExpect(jsonPath("$.returnableQuantity").value(30));
        mvc().perform(get("/api/dispenses/WD1/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returns[0].businessNo").value("WR1"));

        // 台账
        mvc().perform(get("/api/inventory/ledger").param("drugCode", "DRUG-W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].type").value("INBOUND"));
    }

    @Test
    void businessRuleViolationMapsTo422AndLeavesStateUntouched() throws Exception {
        String rxJson = """
                {"prescriptionNo":"RX-W2","patientId":"P1","patientName":"张三",
                 "drugCode":"DRUG-W2","drugName":"药品","totalQuantity":10,
                 "perDoseLimit":5,"allowedDispenseCount":2,
                 "validFrom":"%s","validUntil":"%s"}
                """.formatted(TODAY, TODAY.plusDays(10));
        mvc().perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(rxJson))
                .andExpect(status().isCreated());
        mvc().perform(post("/api/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"drugCode":"DRUG-W2","batchNo":"WB1","quantity":3,
                                 "expiryDate":"%s","inboundNo":"WIN"}
                                """.formatted(TODAY.plusDays(5))))
                .andExpect(status().isCreated());

        // 申请 5 但合格库存只有 3 → 422
        mvc().perform(post("/api/dispenses")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WD2","prescriptionNo":"RX-W2","quantity":5}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("库存不足")));

        // 调剂不存在 → 404
        mvc().perform(get("/api/dispenses/NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchStatusChangeExcludesBatchFromDispense() throws Exception {
        String rxJson = """
                {"prescriptionNo":"RX-W3","patientId":"P1","patientName":"张三",
                 "drugCode":"DRUG-W3","drugName":"药品","totalQuantity":10,
                 "perDoseLimit":10,"allowedDispenseCount":1,
                 "validFrom":"%s","validUntil":"%s"}
                """.formatted(TODAY, TODAY.plusDays(10));
        mvc().perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(rxJson))
                .andExpect(status().isCreated());
        mvc().perform(post("/api/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"drugCode":"DRUG-W3","batchNo":"WB1","quantity":10,
                                 "expiryDate":"%s","inboundNo":"WIN"}
                                """.formatted(TODAY.plusDays(5))))
                .andExpect(status().isCreated());
        long batchId = batchRepository.findByDrugCodeAndBatchNo("DRUG-W3", "WB1")
                .orElseThrow().getId();

        mvc().perform(put("/api/inventory/batches/" + batchId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECALLED\"}"))
                .andExpect(status().isNoContent());

        mvc().perform(post("/api/dispenses")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WD3","prescriptionNo":"RX-W3","quantity":10}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidRequestBodyMapsTo400() throws Exception {
        mvc().perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"prescriptionNo":"","totalQuantity":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }
}
