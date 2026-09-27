package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.FixedClockConfig;
import com.chris64233.pharmacydispense.TestDbCleaner;
import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.DispenseItem;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DrugBatch;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionStatus;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import com.chris64233.pharmacydispense.repository.DrugBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {
        com.chris64233.pharmacydispense.PharmacyDispenseApplication.class,
        FixedClockConfig.class})
class PharmacyServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Autowired
    private PharmacyService service;
    @Autowired
    private DrugBatchRepository batchRepo;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        TestDbCleaner.cleanAll(jdbc);
    }

    private Long prescription(long total, long maxPer, int times) {
        return service.createPrescription(new CreatePrescriptionRequest(
                "张三", "D001", "阿莫西林", total, maxPer,
                TODAY.minusDays(1), TODAY.plusDays(30), times)).getId();
    }

    private void batch(String no, long qty, LocalDate expiry) {
        service.createBatch(new CreateBatchRequest("D001", no, qty, expiry));
    }

    private DrugBatch batch(String no) {
        return batchRepo.findAll().stream()
                .filter(b -> b.getBatchNo().equals(no)).findFirst().orElseThrow();
    }

    private Map<String, Long> quantityByBatchNo(DispenseRecord r) {
        return r.getItems().stream().collect(Collectors.toMap(
                DispenseItem::getBatchNo, DispenseItem::getQuantity));
    }

    // ---------- 规则 1/3：FEFO 多批次扣减 ----------

    @Test
    void dispense_uses_multiple_batches_feFo_order() {
        Long pid = prescription(100, 100, 3);
        batch("B1", 30, TODAY.plusDays(5));
        batch("B2", 100, TODAY.plusDays(20));
        batch("B3", 40, TODAY.plusDays(10));

        DispenseRecord r = service.dispense(pid, "BIZ-1", 80);

        assertThat(r.getQuantity()).isEqualTo(80);
        assertThat(quantityByBatchNo(r)).containsExactlyInAnyOrderEntriesOf(
                Map.of("B1", 30L, "B3", 40L, "B2", 10L));
        assertThat(batch("B1").getQuantity()).isZero();
        assertThat(batch("B3").getQuantity()).isZero();
        assertThat(batch("B2").getQuantity()).isEqualTo(90);

        Prescription p = service.getPrescription(pid);
        assertThat(p.getDispensedQuantity()).isEqualTo(80);
        assertThat(p.remainingQuantity()).isEqualTo(20);
        assertThat(p.getDispensedCount()).isEqualTo(1);
    }

    // ---------- 规则 2：过期/召回/冻结批次不可用 ----------

    @Test
    void expired_frozen_and_recalled_batches_are_unusable() {
        Long pid = prescription(100, 100, 1);
        batch("EXPIRED", 50, TODAY.minusDays(1));   // 昨天到期
        batch("EXPIRE-TODAY", 10, TODAY);           // 今天到期：仍可用
        Long frozenId = batchRepo.findAll().stream()
                .filter(b -> b.getBatchNo().equals("EXPIRE-TODAY")).findFirst().orElseThrow().getId();
        batch("FROZEN", 50, TODAY.plusDays(3));
        batch("RECALLED", 50, TODAY.plusDays(4));
        batch("OK", 50, TODAY.plusDays(10));

        Long frozen = batchRepo.findAll().stream()
                .filter(b -> b.getBatchNo().equals("FROZEN")).findFirst().orElseThrow().getId();
        Long recalled = batchRepo.findAll().stream()
                .filter(b -> b.getBatchNo().equals("RECALLED")).findFirst().orElseThrow().getId();
        service.changeBatchStatus(frozen, BatchStatus.FROZEN);
        service.changeBatchStatus(recalled, BatchStatus.RECALLED);

        // 冻结当日到期批次，使合格库存仅来自 OK 批次
        service.changeBatchStatus(frozenId, BatchStatus.FROZEN);

        // 需要 80，合格只有 50 → 整体失败
        assertThatThrownBy(() -> service.dispense(pid, "BIZ-X", 80))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("合格批次库存不足");

        // 没有任何批次被扣减
        assertThat(batch("EXPIRED").getQuantity()).isEqualTo(50);
        assertThat(batch("FROZEN").getQuantity()).isEqualTo(50);
        assertThat(batch("RECALLED").getQuantity()).isEqualTo(50);
        assertThat(batch("OK").getQuantity()).isEqualTo(50);
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isZero();

        // 合格库存足够时只扣合格批次
        DispenseRecord r = service.dispense(pid, "BIZ-Y", 50);
        assertThat(quantityByBatchNo(r)).isEqualTo(Map.of("OK", 50L));
    }

    // ---------- 规则 3：库存不足整体失败，无部分扣减 ----------

    @Test
    void insufficient_stock_fails_atomically() {
        Long pid = prescription(100, 100, 2);
        batch("A", 30, TODAY.plusDays(1));
        batch("B", 19, TODAY.plusDays(2));

        assertThatThrownBy(() -> service.dispense(pid, "BIZ-FAIL", 50))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(batch("A").getQuantity()).isEqualTo(30);
        assertThat(batch("B").getQuantity()).isEqualTo(19);
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isZero();
    }

    // ---------- 规则 1：单次上限、剩余量、次数 ----------

    @Test
    void enforces_per_dispense_limit_remaining_and_count() {
        Long pid = prescription(20, 10, 2);
        batch("A", 100, TODAY.plusDays(5));

        assertThatThrownBy(() -> service.dispense(pid, "OVER-LIMIT", 11))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("单次上限");

        service.dispense(pid, "D1", 10);
        service.dispense(pid, "D2", 10);

        Prescription p = service.getPrescription(pid);
        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(p.remainingQuantity()).isZero();

        assertThatThrownBy(() -> service.dispense(pid, "D3", 1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cumulative_quantity_cannot_exceed_total_even_within_count() {
        // 总量 15、单次上限 10、允许 3 次：第二次只能取 5
        Long pid = prescription(15, 10, 3);
        batch("A", 100, TODAY.plusDays(5));

        service.dispense(pid, "D1", 10);
        assertThatThrownBy(() -> service.dispense(pid, "D2", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("剩余量");

        DispenseRecord d2 = service.dispense(pid, "D2", 5);
        assertThat(d2.getQuantity()).isEqualTo(5);
        assertThat(service.getPrescription(pid).getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
    }

    @Test
    void prescription_outside_validity_window_is_rejected() {
        Long pid = service.createPrescription(new CreatePrescriptionRequest(
                "李四", "D001", "阿莫西林", 10, 10,
                TODAY.plusDays(1), TODAY.plusDays(5), 1)).getId();
        batch("A", 100, TODAY.plusDays(10));

        assertThatThrownBy(() -> service.dispense(pid, "LATE", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("有效期");
    }

    // ---------- 规则 4：幂等 ----------

    @Test
    void same_biz_no_is_idempotent() {
        Long pid = prescription(100, 50, 3);
        batch("A", 100, TODAY.plusDays(5));

        DispenseRecord first = service.dispense(pid, "IDEM-1", 20);
        DispenseRecord replay = service.dispense(pid, "IDEM-1", 20);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(service.listDispenses(pid)).hasSize(1);
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isEqualTo(20);
        assertThat(batch("A").getQuantity()).isEqualTo(80);
    }

    @Test
    void same_biz_no_with_different_params_conflicts() {
        Long pid = prescription(100, 50, 3);
        batch("A", 100, TODAY.plusDays(5));

        service.dispense(pid, "IDEM-2", 20);
        assertThatThrownBy(() -> service.dispense(pid, "IDEM-2", 21))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ---------- 规则 4：作废语义 ----------

    @Test
    void cancelled_prescription_cannot_be_dispensed() {
        Long pid = prescription(100, 100, 1);
        batch("A", 100, TODAY.plusDays(5));

        service.cancelPrescription(pid);
        assertThat(service.getPrescription(pid).getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);

        assertThatThrownBy(() -> service.dispense(pid, "AFTER-CANCEL", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("作废");
    }

    @Test
    void cannot_cancel_after_dispense_without_return() {
        Long pid = prescription(100, 100, 2);
        batch("A", 100, TODAY.plusDays(5));
        service.dispense(pid, "D1", 30);

        assertThatThrownBy(() -> service.cancelPrescription(pid))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("退药");
    }

    // ---------- 规则 5：退药链、批次回补、余额恢复 ----------

    @Test
    void partial_returns_restore_batches_and_remaining_chain() {
        Long pid = prescription(100, 100, 2);
        batch("B1", 30, TODAY.plusDays(1));
        batch("B2", 100, TODAY.plusDays(10));

        DispenseRecord d = service.dispense(pid, "D1", 80); // B1:30, B2:50
        assertThat(batch("B1").getQuantity()).isZero();
        assertThat(batch("B2").getQuantity()).isEqualTo(50);

        // 部分退药 30：先回补 B1
        ReturnRecord r1 = service.returnDispense(d.getId(), "R1", 30);
        assertThat(batch("B1").getQuantity()).isEqualTo(30);
        assertThat(batch("B2").getQuantity()).isEqualTo(50);
        // 部分退药不回补次数，只回补剂量
        assertThat(service.getPrescription(pid).getDispensedCount()).isEqualTo(1);
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isEqualTo(50);

        // 再退 50：回补 B2，全额退清 → 回补次数
        ReturnRecord r2 = service.returnDispense(d.getId(), "R2", 50);
        assertThat(batch("B2").getQuantity()).isEqualTo(100);
        Prescription p = service.getPrescription(pid);
        assertThat(p.getDispensedQuantity()).isZero();
        assertThat(p.getDispensedCount()).isZero();
        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);

        // 退药链查询
        List<ReturnRecord> chain = service.listReturns(d.getId());
        assertThat(chain).extracting(ReturnRecord::getBizNo).containsExactly("R1", "R2");
        assertThat(r1.getId()).isNotEqualTo(r2.getId());

        // 已全额退清，不能再退
        assertThatThrownBy(() -> service.returnDispense(d.getId(), "R3", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("全额退清");
    }

    @Test
    void return_cannot_exceed_original_dispense_quantity() {
        Long pid = prescription(100, 100, 2);
        batch("A", 100, TODAY.plusDays(5));
        DispenseRecord d = service.dispense(pid, "D1", 40);

        assertThatThrownBy(() -> service.returnDispense(d.getId(), "R-BIG", 41))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("可退量");
        assertThatThrownBy(() -> service.returnDispense(d.getId(), "R-BIG2", 100))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(batch("A").getQuantity()).isEqualTo(60);
    }

    @Test
    void return_is_idempotent_by_biz_no() {
        Long pid = prescription(100, 100, 2);
        batch("A", 100, TODAY.plusDays(5));
        DispenseRecord d = service.dispense(pid, "D1", 40);

        ReturnRecord r1 = service.returnDispense(d.getId(), "RID", 10);
        ReturnRecord r2 = service.returnDispense(d.getId(), "RID", 10);
        assertThat(r1.getId()).isEqualTo(r2.getId());
        assertThat(service.listReturns(d.getId())).hasSize(1);
        assertThat(batch("A").getQuantity()).isEqualTo(70);
    }

    // ---------- 规则 6：台账与调剂批次查询 ----------

    @Test
    void ledger_and_dispense_queries() {
        Long pid = prescription(100, 100, 2);
        batch("B1", 30, TODAY.plusDays(2));
        batch("B2", 70, TODAY.plusDays(9));
        service.dispense(pid, "D1", 40);

        List<DrugBatch> ledger = service.ledger("D001");
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(0).getBatchNo()).isEqualTo("B1"); // 到期日升序

        List<DispenseRecord> dispenses = service.listDispenses(pid);
        assertThat(dispenses).hasSize(1);
        assertThat(quantityByBatchNo(dispenses.get(0))).isEqualTo(Map.of("B1", 30L, "B2", 10L));
    }
}
