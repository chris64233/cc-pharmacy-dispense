package com.chris64233.pharmacydispense.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PharmacyApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        com.chris64233.pharmacydispense.TestDbCleaner.cleanAll(jdbc);
    }

    private long createPrescription(long total, long max, int times) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "patientName", "赵六",
                "drugCode", "API-1",
                "drugName", "接口药",
                "totalQuantity", total,
                "maxPerDispense", max,
                "validFrom", LocalDate.now().minusDays(1).toString(),
                "validTo", LocalDate.now().plusDays(30).toString(),
                "allowedDispenseCount", times));
        String json = mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("id").asLong();
    }

    private long createBatch(String no, long qty, String expiry) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "drugCode", "API-1", "batchNo", no,
                "quantity", qty, "expiryDate", expiry));
        String json = mvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("id").asLong();
    }

    @Test
    void full_lifecycle_over_http() throws Exception {
        long pid = createPrescription(100, 60, 3);
        createBatch("BA", 30, LocalDate.now().plusDays(2).toString());
        createBatch("BB", 100, LocalDate.now().plusDays(20).toString());

        // 调剂 60：BA 30 + BB 30（FEFO）
        mvc.perform(post("/api/prescriptions/" + pid + "/dispenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "HTTP-1", "quantity", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(60))
                .andExpect(jsonPath("$.items.length()").value(2));

        // 幂等重放
        mvc.perform(post("/api/prescriptions/" + pid + "/dispenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "HTTP-1", "quantity", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bizNo").value("HTTP-1"));

        // 处方余额
        mvc.perform(get("/api/prescriptions/" + pid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(40))
                .andExpect(jsonPath("$.dispensedCount").value(1))
                .andExpect(jsonPath("$.remainingCount").value(2));

        // 超单次上限
        mvc.perform(post("/api/prescriptions/" + pid + "/dispenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "HTTP-2", "quantity", 61))))
                .andExpect(status().isUnprocessableEntity());

        // 冻结 BB 后合格库存不足（BA 已空）
        long bbId = om.readTree(mvc.perform(get("/api/batches?drugCode=API-1"))
                        .andReturn().getResponse().getContentAsString())
                .get(1).get("id").asLong();
        mvc.perform(patch("/api/batches/" + bbId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("status", "FROZEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mvc.perform(post("/api/prescriptions/" + pid + "/dispenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "HTTP-3", "quantity", 10))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));

        // 解冻后调剂 40
        mvc.perform(patch("/api/batches/" + bbId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("status", "NORMAL"))))
                .andExpect(status().isOk());
        long dispenseId = om.readTree(mvc.perform(post("/api/prescriptions/" + pid + "/dispenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of("bizNo", "HTTP-4", "quantity", 40))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // 退药链
        mvc.perform(post("/api/dispenses/" + dispenseId + "/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "RET-1", "quantity", 15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(15));
        mvc.perform(get("/api/dispenses/" + dispenseId + "/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 超量退药
        mvc.perform(post("/api/dispenses/" + dispenseId + "/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bizNo", "RET-2", "quantity", 30))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("可退量")));

        // 库存台账
        mvc.perform(get("/api/batches?drugCode=API-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void validation_errors_return_400_and_unknown_resources_404() throws Exception {
        String bad = om.writeValueAsString(Map.of(
                "patientName", "",
                "drugCode", "X",
                "totalQuantity", 0,
                "maxPerDispense", 0,
                "validFrom", LocalDate.now().toString(),
                "validTo", LocalDate.now().toString(),
                "allowedDispenseCount", 1));
        mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/prescriptions/999999"))
                .andExpect(status().isNotFound());
    }
}
