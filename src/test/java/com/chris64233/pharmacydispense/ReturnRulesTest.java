package com.chris64233.pharmacydispense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.error.NotFoundException;
import com.chris64233.pharmacydispense.service.DispenseLineView;
import com.chris64233.pharmacydispense.service.DispenseQueryService;
import com.chris64233.pharmacydispense.service.DispenseService;
import com.chris64233.pharmacydispense.service.ReturnQueryService;
import com.chris64233.pharmacydispense.service.ReturnService;
import com.chris64233.pharmacydispense.service.ReturnView;

/**
 * 退药规则：按原批次恢复库存与处方余额、不得超原调剂量、次数不恢复、幂等、退药链查询。
 */
class ReturnRulesTest extends AbstractIntegrationTest {

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ReturnQueryService returnQueryService;

    @Autowired
    private DispenseQueryService dispenseQueryService;

    @Test
    void returnRestoresOriginalBatchesAndPrescriptionBalance() {
        createPrescription("RX-R1", "DRUG-R", 100, 50, 5);
        inbound("DRUG-R", "B1", 30, TODAY.plusDays(2));
        inbound("DRUG-R", "B2", 40, TODAY.plusDays(8));

        dispenseService.dispense("DR-1", "RX-R1", 50);
        assertThat(batchQuantity("DRUG-R", "B1")).isZero();
        assertThat(batchQuantity("DRUG-R", "B2")).isEqualTo(20);
        assertThat(dispenseQueryService.balance("RX-R1").remainingQuantity()).isEqualTo(50);

        // 全退：按原调剂行恢复 B1=30、B2=20
        var outcome = returnService.returnMedicine("RT-1", "DR-1", 50);
        assertThat(outcome.replayed()).isFalse();

        assertThat(batchQuantity("DRUG-R", "B1")).isEqualTo(30);
        assertThat(batchQuantity("DRUG-R", "B2")).isEqualTo(40);
        var balance = dispenseQueryService.balance("RX-R1");
        assertThat(balance.remainingQuantity()).isEqualTo(100);
        assertThat(balance.dispensedQuantity()).isZero();
        // 调剂次数不恢复
        assertThat(balance.dispensedCount()).isEqualTo(1);
    }

    @Test
    void partialReturnFollowsOriginalLineOrderAndCannotExceedDispensed() {
        createPrescription("RX-R2", "DRUG-R2", 100, 50, 5);
        inbound("DRUG-R2", "B1", 30, TODAY.plusDays(2));
        inbound("DRUG-R2", "B2", 40, TODAY.plusDays(8));
        dispenseService.dispense("DR-2", "RX-R2", 50);

        // 先退 40：B1 全退 30，B2 退 10
        returnService.returnMedicine("RT-2A", "DR-2", 40);
        assertThat(batchQuantity("DRUG-R2", "B1")).isEqualTo(30);
        assertThat(batchQuantity("DRUG-R2", "B2")).isEqualTo(30);
        assertThat(dispenseQueryService.balance("RX-R2").remainingQuantity()).isEqualTo(90);

        // 再退超过剩余可退量（仅剩 10）→ 拒绝，库存不变
        assertThatThrownBy(() -> returnService.returnMedicine("RT-2B", "DR-2", 11))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("超过原调剂可退量");
        assertThat(batchQuantity("DRUG-R2", "B2")).isEqualTo(30);

        // 退完剩余 10，全部回到 B2
        returnService.returnMedicine("RT-2C", "DR-2", 10);
        assertThat(batchQuantity("DRUG-R2", "B2")).isEqualTo(40);

        // 已退满，再退任何数量都被拒
        assertThatThrownBy(() -> returnService.returnMedicine("RT-2D", "DR-2", 1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void returnRestoresStockEvenIfBatchLaterFrozen() {
        createPrescription("RX-R3", "DRUG-R3", 50, 50, 2);
        var b1 = inbound("DRUG-R3", "B1", 50, TODAY.plusDays(5));
        dispenseService.dispense("DR-3", "RX-R3", 50);
        inventoryService.changeBatchStatus(b1.getId(), BatchStatus.FROZEN);

        returnService.returnMedicine("RT-3", "DR-3", 50);

        // 库存回到被冻结批次（数量恢复，但状态仍冻结，不参与新调剂）
        var reloaded = batchRepository.findById(b1.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(50);
        assertThat(reloaded.getStatus()).isEqualTo(BatchStatus.FROZEN);
        assertThatThrownBy(() -> dispenseService.dispense("DR-3B", "RX-R3", 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("合格批次库存不足");
    }

    @Test
    void returnAgainstUnknownDispenseRejected() {
        assertThatThrownBy(() -> returnService.returnMedicine("RT-X", "NO-SUCH", 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnBusinessNoIsIdempotentAndDifferentPayloadConflicts() {
        createPrescription("RX-R4", "DRUG-R4", 50, 50, 2);
        inbound("DRUG-R4", "B1", 50, TODAY.plusDays(5));
        dispenseService.dispense("DR-4", "RX-R4", 50);

        var first = returnService.returnMedicine("RBT-1", "DR-4", 20);
        var replay = returnService.returnMedicine("RBT-1", "DR-4", 20);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.record().getId()).isEqualTo(first.record().getId());

        // 只退了 20
        assertThat(returnQueryService.returnChain("DR-4").returnedQuantity()).isEqualTo(20);
        assertThat(batchQuantity("DRUG-R4", "B1")).isEqualTo(20);

        assertThatThrownBy(() -> returnService.returnMedicine("RBT-1", "DR-4", 5))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void returnChainQueryShowsReturnedAndReturnableWithBatchLines() {
        createPrescription("RX-R5", "DRUG-R5", 100, 60, 5);
        inbound("DRUG-R5", "B1", 30, TODAY.plusDays(2));
        inbound("DRUG-R5", "B2", 50, TODAY.plusDays(9));
        dispenseService.dispense("DR-5", "RX-R5", 60);

        returnService.returnMedicine("RT-5A", "DR-5", 20);
        returnService.returnMedicine("RT-5B", "DR-5", 15);

        ReturnView.Chain chain = returnQueryService.returnChain("DR-5");
        assertThat(chain.dispensedQuantity()).isEqualTo(60);
        assertThat(chain.returnedQuantity()).isEqualTo(35);
        assertThat(chain.returnableQuantity()).isEqualTo(25);
        assertThat(chain.returns()).hasSize(2);
        // 第一次退 20 全部来自 B1；第二次退 15：B1 剩余可退 10 + B2 5
        assertThat(chain.returns().get(0).lines())
                .extracting(DispenseLineView::batchNo, DispenseLineView::quantity)
                .containsExactly(tuple("B1", 20L));
        assertThat(chain.returns().get(1).lines())
                .extracting(DispenseLineView::batchNo, DispenseLineView::quantity)
                .containsExactly(tuple("B1", 10L), tuple("B2", 5L));
    }
}
