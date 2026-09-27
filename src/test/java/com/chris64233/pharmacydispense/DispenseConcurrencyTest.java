package com.chris64233.pharmacydispense;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chris64233.pharmacydispense.domain.PrescriptionStatus;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.service.DispenseQueryService;
import com.chris64233.pharmacydispense.service.DispenseService;
import com.chris64233.pharmacydispense.service.ReturnQueryService;
import com.chris64233.pharmacydispense.service.ReturnService;

/**
 * 并发安全测试：真实多线程 + 独立事务，验证不超量、不超次数、库存不为负、
 * 作废与完整调剂二选一、业务号幂等、退药不超额。
 */
class DispenseConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private ReturnService returnService;

    @Autowired
    private DispenseQueryService dispenseQueryService;

    @Autowired
    private ReturnQueryService returnQueryService;

    @Test
    void concurrentDispensesSamePrescriptionNeverOversell() throws Exception {
        createPrescription("RX-C1", "DRUG-C1", 100, 20, 10);
        inbound("DRUG-C1", "B1", 60, TODAY.plusDays(3));
        inbound("DRUG-C1", "B2", 40, TODAY.plusDays(9));

        int threads = 8;
        RaceResult result = runRace(threads, i ->
                dispenseService.dispense("CD1-" + i, "RX-C1", 20) != null);

        // 库存/余额恰好够 5 次：5 成功 3 失败
        assertThat(result.successCount()).hasValue(5);
        assertThat(result.failures()).isNotEmpty().allMatch(BusinessRuleException.class::isInstance);

        var balance = dispenseQueryService.balance("RX-C1");
        assertThat(balance.dispensedQuantity()).isEqualTo(100);
        assertThat(balance.remainingQuantity()).isZero();
        assertThat(balance.dispensedCount()).isEqualTo(5);
        assertThat(batchQuantity("DRUG-C1", "B1")).isZero();
        assertThat(batchQuantity("DRUG-C1", "B2")).isZero();
        assertThat(dispenseQueryService.listDispenses("RX-C1")).hasSize(5);
    }

    @Test
    void concurrentDispensesAcrossPrescriptionsSharingBatchesNeverGoNegative() throws Exception {
        // 两张处方共享同一批库存 70；每张处方都声称需要 100
        createPrescription("RX-C2A", "DRUG-C2", 100, 10, 100);
        createPrescription("RX-C2B", "DRUG-C2", 100, 10, 100);
        inbound("DRUG-C2", "B1", 70, TODAY.plusDays(10));

        int threads = 14;
        RaceResult result = runRace(threads, i -> {
            String rx = i < 7 ? "RX-C2A" : "RX-C2B";
            dispenseService.dispense("CD2-" + i, rx, 10);
            return true;
        });

        assertThat(result.successCount()).hasValue(7);
        assertThat(result.failures()).allMatch(BusinessRuleException.class::isInstance);
        // 两处方累计调剂 = 共享库存 70，库存绝不为负
        long dispensedA = dispenseQueryService.balance("RX-C2A").dispensedQuantity();
        long dispensedB = dispenseQueryService.balance("RX-C2B").dispensedQuantity();
        assertThat(dispensedA + dispensedB).isEqualTo(70);
        assertThat(batchQuantity("DRUG-C2", "B1")).isZero();
    }

    @Test
    void concurrentVoidAndDispenseProduceExactlyOneOutcome() throws Exception {
        // 重复 20 轮独立数据，每轮断言“已作废”或“存在完整调剂”二者必居其一
        for (int roundIndex = 0; roundIndex < 20; roundIndex++) {
            final int round = roundIndex;
            final String rx = "RX-C3-" + round;
            createPrescription(rx, "DRUG-C3-" + round, 50, 50, 1);
            inbound("DRUG-C3-" + round, "B1", 50, TODAY.plusDays(10));

            CyclicBarrier barrier = new CyclicBarrier(2);
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            Thread dispenser = new Thread(() -> {
                await(barrier, errors);
                try {
                    dispenseService.dispense("CD3-" + round, rx, 50);
                } catch (BusinessRuleException expected) {
                    // 作废先提交，调剂被拒
                } catch (Throwable t) {
                    errors.add(t);
                }
            }, "dispenser-" + round);
            Thread voider = new Thread(() -> {
                await(barrier, errors);
                try {
                    prescriptionService.voidPrescription(rx);
                } catch (BusinessRuleException expected) {
                    // 调剂先提交，作废被拒
                } catch (Throwable t) {
                    errors.add(t);
                }
            }, "voider-" + round);
            dispenser.start();
            voider.start();
            dispenser.join(30_000);
            voider.join(30_000);
            assertThat(errors).as("round %d 不应出现锁超时等非预期异常", round).isEmpty();
            assertThat(dispenser.isAlive()).isFalse();
            assertThat(voider.isAlive()).isFalse();

            var balance = dispenseQueryService.balance(rx);
            boolean voidWon = balance.status() == PrescriptionStatus.VOID
                    && balance.dispensedQuantity() == 0;
            boolean dispenseWon = balance.status() == PrescriptionStatus.ACTIVE
                    && balance.dispensedQuantity() == 50
                    && balance.dispensedCount() == 1;
            assertThat(voidWon || dispenseWon)
                    .as("round %d 必须二选一：voidWon=%b dispenseWon=%b balance=%s",
                            round, voidWon, dispenseWon, balance)
                    .isTrue();
            assertThat(voidWon && dispenseWon).isFalse();
        }
    }

    @Test
    void concurrentSameBusinessNoCreatesExactlyOneDispense() throws Exception {
        createPrescription("RX-C4", "DRUG-C4", 100, 20, 10);
        inbound("DRUG-C4", "B1", 100, TODAY.plusDays(10));

        int threads = 4;
        RaceResult result = runRace(threads, i -> {
            // 所有线程使用同一个业务号、相同请求内容
            dispenseService.dispense("SAME-BIZ-NO", "RX-C4", 20);
            return true;
        });

        assertThat(result.successCount()).hasValue(threads);
        assertThat(result.failures()).isEmpty();
        var balance = dispenseQueryService.balance("RX-C4");
        assertThat(balance.dispensedQuantity()).isEqualTo(20);
        assertThat(balance.dispensedCount()).isEqualTo(1);
        assertThat(batchQuantity("DRUG-C4", "B1")).isEqualTo(80);
        assertThat(dispenseQueryService.listDispenses("RX-C4")).hasSize(1);
    }

    @Test
    void concurrentReturnsNeverExceedOriginalDispense() throws Exception {
        createPrescription("RX-C5", "DRUG-C5", 100, 60, 10);
        inbound("DRUG-C5", "B1", 30, TODAY.plusDays(2));
        inbound("DRUG-C5", "B2", 30, TODAY.plusDays(8));
        dispenseService.dispense("CD5-D", "RX-C5", 60);

        int threads = 6;
        RaceResult result = runRace(threads, i -> {
            // 每个退药请求独立业务号，各退 15；总可退只有 60 → 仅 4 个成功
            returnService.returnMedicine("CD5-R-" + i, "CD5-D", 15);
            return true;
        });

        assertThat(result.successCount())
                .as("failures=%s", result.failures().stream()
                        .map(t -> t.getClass().getSimpleName() + ":" + t.getMessage())
                        .toList())
                .hasValue(4);
        assertThat(result.failures()).allMatch(BusinessRuleException.class::isInstance);

        var chain = returnQueryService.returnChain("CD5-D");
        assertThat(chain.returnedQuantity()).isEqualTo(60);
        assertThat(chain.returnableQuantity()).isZero();
        assertThat(batchQuantity("DRUG-C5", "B1")).isEqualTo(30);
        assertThat(batchQuantity("DRUG-C5", "B2")).isEqualTo(30);
        // 处方余额全部恢复，次数仍是 1
        var balance = dispenseQueryService.balance("RX-C5");
        assertThat(balance.dispensedQuantity()).isZero();
        assertThat(balance.remainingQuantity()).isEqualTo(100);
        assertThat(balance.dispensedCount()).isEqualTo(1);
    }

    private interface RaceAction {
        boolean run(int index) throws Exception;
    }

    private record RaceResult(AtomicInteger successCount,
                              ConcurrentLinkedQueue<Throwable> failures) {
    }

    private RaceResult runRace(int threads, RaceAction action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    action.run(index);
                    success.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        return new RaceResult(success, failures);
    }

    private static void await(CyclicBarrier barrier, ConcurrentLinkedQueue<Throwable> errors) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            errors.add(e);
        }
    }
}
