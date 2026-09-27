package com.chris64233.pharmacydispense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.service.DispenseLineView;
import com.chris64233.pharmacydispense.service.DispenseQueryService;
import com.chris64233.pharmacydispense.service.DispenseService;
import com.chris64233.pharmacydispense.service.DispenseView;
import com.chris64233.pharmacydispense.service.PrescriptionBalanceView;
import com.chris64233.pharmacydispense.service.StockMovementView;
import com.chris64233.pharmacydispense.service.StockQueryService;

/**
 * 调剂业务规则：批次资格、FEFO、整体失败、单次/累计/次数上限、作废互斥、幂等与查询。
 */
class DispenseRulesTest extends AbstractIntegrationTest {

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private DispenseQueryService dispenseQueryService;

    @Autowired
    private StockQueryService stockQueryService;

    @Test
    void multiBatchDeductionFollowsFefoOrder() {
        createPrescription("RX-1", "DRUG-A", 100, 100, 3);
        // 三个批次，入库顺序故意与到期顺序相反
        inbound("DRUG-A", "B-LATE", 40, TODAY.plusDays(30));
        inbound("DRUG-A", "B-MID", 30, TODAY.plusDays(10));
        inbound("DRUG-A", "B-EARLY", 30, TODAY.plusDays(1));

        dispenseService.dispense("D-1", "RX-1", 50);

        DispenseView view = dispenseQueryService.getDispense("D-1");
        assertThat(view.lines()).extracting(DispenseLineView::batchNo)
                .containsExactly("B-EARLY", "B-MID");
        assertThat(view.lines()).extracting(DispenseLineView::quantity)
                .containsExactly(30L, 20L);
        assertThat(batchQuantity("DRUG-A", "B-EARLY")).isZero();
        assertThat(batchQuantity("DRUG-A", "B-MID")).isEqualTo(10);
        assertThat(batchQuantity("DRUG-A", "B-LATE")).isEqualTo(40);
    }

    @Test
    void expiredRecalledAndFrozenBatchesAreNotEligible() {
        createPrescription("RX-2", "DRUG-B", 100, 100, 2);
        var expired = inbound("DRUG-B", "B-EXP", 40, TODAY.minusDays(1));
        var recalled = inbound("DRUG-B", "B-REC", 20, TODAY.plusDays(5));
        var frozen = inbound("DRUG-B", "B-FRZ", 20, TODAY.plusDays(6));
        inbound("DRUG-B", "B-OK", 25, TODAY.plusDays(20));
        inventoryService.changeBatchStatus(recalled.getId(), BatchStatus.RECALLED);
        inventoryService.changeBatchStatus(frozen.getId(), BatchStatus.FROZEN);
        inventoryService.changeBatchStatus(expired.getId(), BatchStatus.EXPIRED);

        // 合格库存只有 25，申请 30 → 失败
        assertThatThrownBy(() -> dispenseService.dispense("D-2", "RX-2", 30))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("合格批次库存不足");

        // 不合格批次库存原样保留，没有任何部分扣减
        assertThat(batchQuantity("DRUG-B", "B-EXP")).isEqualTo(40);
        assertThat(batchQuantity("DRUG-B", "B-REC")).isEqualTo(20);
        assertThat(batchQuantity("DRUG-B", "B-FRZ")).isEqualTo(20);
        assertThat(batchQuantity("DRUG-B", "B-OK")).isEqualTo(25);
        assertThat(dispenseQueryService.listDispenses("RX-2")).isEmpty();

        // 合格量内可调剂，且只动合格批次
        dispenseService.dispense("D-2B", "RX-2", 25);
        assertThat(batchQuantity("DRUG-B", "B-OK")).isZero();
    }

    @Test
    void batchExpiringTodayIsStillUsable() {
        createPrescription("RX-2E", "DRUG-BE", 10, 10, 1);
        inbound("DRUG-BE", "B-TODAY", 10, TODAY);
        dispenseService.dispense("D-2E", "RX-2E", 10);
        assertThat(batchQuantity("DRUG-BE", "B-TODAY")).isZero();
    }

    @Test
    void insufficientStockFailsAtomicallyWithoutPartialDeduction() {
        createPrescription("RX-3", "DRUG-C", 100, 100, 2);
        inbound("DRUG-C", "B1", 30, TODAY.plusDays(2));
        inbound("DRUG-C", "B2", 15, TODAY.plusDays(5));

        // 需要 60，总合格库存仅 45
        assertThatThrownBy(() -> dispenseService.dispense("D-3", "RX-3", 60))
                .isInstanceOf(BusinessRuleException.class);

        // 两批库存均未被扣减
        assertThat(batchQuantity("DRUG-C", "B1")).isEqualTo(30);
        assertThat(batchQuantity("DRUG-C", "B2")).isEqualTo(15);
        assertThat(stockQueryService.ledger("DRUG-C"))
                .extracting(StockMovementView::type)
                .containsOnly("INBOUND");
    }

    @Test
    void singleDoseCannotExceedPerDoseLimit() {
        createPrescription("RX-4", "DRUG-D", 100, 30, 5);
        inbound("DRUG-D", "B1", 100, TODAY.plusDays(10));

        assertThatThrownBy(() -> dispenseService.dispense("D-4", "RX-4", 31))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("单次上限");
        assertThat(batchQuantity("DRUG-D", "B1")).isEqualTo(100);
    }

    @Test
    void cumulativeDispenseCannotExceedRemainingQuantity() {
        createPrescription("RX-5", "DRUG-E", 50, 20, 10);
        inbound("DRUG-E", "B1", 100, TODAY.plusDays(10));

        dispenseService.dispense("D-5A", "RX-5", 20);
        dispenseService.dispense("D-5B", "RX-5", 20);

        // 剩余 10，申请 20 被拒；申请 10 成功
        assertThatThrownBy(() -> dispenseService.dispense("D-5C", "RX-5", 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("处方剩余量");

        dispenseService.dispense("D-5D", "RX-5", 10);
        PrescriptionBalanceView balance = dispenseQueryService.balance("RX-5");
        assertThat(balance.remainingQuantity()).isZero();
        assertThat(balance.dispensedQuantity()).isEqualTo(50);
        assertThat(balance.dispensedCount()).isEqualTo(3);
    }

    @Test
    void dispenseCountCannotExceedAllowedTimes() {
        createPrescription("RX-6", "DRUG-F", 100, 10, 2);
        inbound("DRUG-F", "B1", 100, TODAY.plusDays(10));

        dispenseService.dispense("D-6A", "RX-6", 10);
        dispenseService.dispense("D-6B", "RX-6", 10);

        assertThatThrownBy(() -> dispenseService.dispense("D-6C", "RX-6", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("允许调剂次数");
    }

    @Test
    void voidedPrescriptionCannotDispenseAndVoidAfterDispenseRejected() {
        createPrescription("RX-7", "DRUG-G", 50, 50, 2);
        inbound("DRUG-G", "B1", 50, TODAY.plusDays(10));

        prescriptionService.voidPrescription("RX-7");
        assertThatThrownBy(() -> dispenseService.dispense("D-7A", "RX-7", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已作废");

        // 已发生完整调剂的处方不能作废
        createPrescription("RX-8", "DRUG-H", 50, 50, 2);
        inbound("DRUG-H", "B1", 50, TODAY.plusDays(10));
        dispenseService.dispense("D-8A", "RX-8", 10);
        assertThatThrownBy(() -> prescriptionService.voidPrescription("RX-8"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能作废");
    }

    @Test
    void dispenseOutsideValidityWindowRejected() {
        prescriptionService.create("RX-9", "P", "患者", "DRUG-I", "药品", 10, 10, 1,
                TODAY.plusDays(1), TODAY.plusDays(5));
        inbound("DRUG-I", "B1", 10, TODAY.plusDays(30));
        assertThatThrownBy(() -> dispenseService.dispense("D-9", "RX-9", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("有效期");
    }

    @Test
    void sameBusinessNoIsIdempotentAndDifferentPayloadConflicts() {
        createPrescription("RX-10", "DRUG-J", 100, 50, 3);
        inbound("DRUG-J", "B1", 100, TODAY.plusDays(10));

        var first = dispenseService.dispense("BIZ-1", "RX-10", 20);
        assertThat(first.replayed()).isFalse();
        var replay = dispenseService.dispense("BIZ-1", "RX-10", 20);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.record().getId()).isEqualTo(first.record().getId());

        // 只调剂了一次：累计 20、次数 1、库存扣 20
        assertThat(dispenseQueryService.balance("RX-10").dispensedQuantity()).isEqualTo(20);
        assertThat(batchQuantity("DRUG-J", "B1")).isEqualTo(80);

        // 同一业务号携带不同数量 → 冲突
        assertThatThrownBy(() -> dispenseService.dispense("BIZ-1", "RX-10", 30))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void dispenseBatchAndLedgerQueriesReflectMovements() {
        createPrescription("RX-11", "DRUG-K", 100, 40, 3);
        inbound("DRUG-K", "B1", 30, TODAY.plusDays(3));
        inbound("DRUG-K", "B2", 40, TODAY.plusDays(8));

        dispenseService.dispense("D-11", "RX-11", 40);

        List<DispenseView> dispenses = dispenseQueryService.listDispenses("RX-11");
        assertThat(dispenses).hasSize(1);
        assertThat(dispenses.get(0).lines()).hasSize(2);

        List<StockMovementView> ledger = stockQueryService.ledger("DRUG-K");
        // 入库 2 条 + 调剂 2 行；结余依次 30,40,0,30
        assertThat(ledger).hasSize(4);
        assertThat(ledger).extracting(StockMovementView::runningBalance)
                .containsExactly(30L, 40L, 0L, 30L);
        assertThat(ledger).extracting(StockMovementView::changeQuantity)
                .containsExactly(30L, 40L, -30L, -10L);
        assertThat(ledger).extracting(StockMovementView::refBusinessNo)
                .containsExactly("IN-B1", "IN-B2", "D-11", "D-11");
    }
}
