package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.FixedClockConfig;
import com.chris64233.pharmacydispense.TestDbCleaner;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 原调剂/退药记录不可修改、不可删除：JPA 实体回调在 UPDATE/DELETE 时拒绝。
 */
@SpringBootTest(classes = {
        com.chris64233.pharmacydispense.PharmacyDispenseApplication.class,
        FixedClockConfig.class})
class RecordImmutabilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Autowired
    private PharmacyService service;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void cleanDb() {
        TestDbCleaner.cleanAll(jdbc);
    }

    private Long setupDispense() {
        Long pid = service.createPrescription(new CreatePrescriptionRequest(
                "王五", "D009", "某药", 100, 100,
                TODAY.minusDays(1), TODAY.plusDays(30), 2)).getId();
        service.createBatch(new CreateBatchRequest("D009", "BB", 100, TODAY.plusDays(10)));
        return service.dispense(pid, "IMM-D", 40).getId();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void dispense_record_cannot_be_updated() {
        DispenseRecord d = em.find(DispenseRecord.class, setupDispense());
        ReflectionTestUtils.setField(d, "quantity", 999L);
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void dispense_record_cannot_be_deleted() {
        DispenseRecord d = em.find(DispenseRecord.class, setupDispense());
        assertThatThrownBy(() -> em.remove(d))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void return_record_cannot_be_updated() {
        Long dispenseId = setupDispense();
        Long returnId = service.returnDispense(dispenseId, "IMM-R", 10).getId();

        ReturnRecord r = em.find(ReturnRecord.class, returnId);
        ReflectionTestUtils.setField(r, "quantity", 1L);
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void return_record_cannot_be_deleted() {
        Long dispenseId = setupDispense();
        Long returnId = service.returnDispense(dispenseId, "IMM-R2", 10).getId();

        ReturnRecord r = em.find(ReturnRecord.class, returnId);
        assertThatThrownBy(() -> em.remove(r))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可修改");
    }
}
