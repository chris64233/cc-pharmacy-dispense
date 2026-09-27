package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.FixedClockConfig;
import com.chris64233.pharmacydispense.TestDbCleaner;
import com.chris64233.pharmacydispense.domain.DrugBatch;
import com.chris64233.pharmacydispense.domain.PrescriptionStatus;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import com.chris64233.pharmacydispense.repository.DrugBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发安全测试：
 * - 并发调剂同一处方不得超量、超次数、库存为负；
 * - 同 bizNo 并发只落一笔调剂（幂等）；
 * - 作废与调剂并发，结果只能是作废或完整调剂之一；
 * - 多张处方共享批次库存时，总扣减不超过库存。
 */
@SpringBootTest(classes = {
        com.chris64233.pharmacydispense.PharmacyDispenseApplication.class,
        FixedClockConfig.class})
class PharmacyConcurrencyTest {

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

    private long newPrescription(long total, long maxPer, int times) {
        return service.createPrescription(new CreatePrescriptionRequest(
                "并发患者", "C001", "并发药", total, maxPer,
                TODAY.minusDays(1), TODAY.plusDays(30), times)).getId();
    }

    private void stock(long qty) {
        service.createBatch(new CreateBatchRequest("C001", "S1", qty, TODAY.plusDays(30)));
    }

    private long stockOf() {
        List<DrugBatch> all = batchRepo.findAll();
        return all.stream().filter(b -> b.getDrugCode().equals("C001"))
                .mapToLong(DrugBatch::getQuantity).sum();
    }

    @Test
    void concurrent_dispenses_never_oversell_or_go_negative() throws Exception {
        long pid = newPrescription(50, 10, 10);
        stock(100);

        int threads = 10;
        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            String biz = "C-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    service.dispense(pid, biz, 10);
                    success.incrementAndGet();
                } catch (BusinessRuleException expected) {
                    // 超量/超次数方失败，属于预期
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(success.get()).isEqualTo(5); // 总量 50 / 每次 10
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isEqualTo(50);
        assertThat(service.getPrescription(pid).getDispensedCount()).isEqualTo(5);
        assertThat(stockOf()).isEqualTo(50); // 100 - 50，绝不为负
    }

    @Test
    void concurrent_same_biz_no_creates_single_dispense() throws Exception {
        long pid = newPrescription(100, 10, 20);
        stock(100);

        int threads = 8;
        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Long> ids = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ids.add(service.dispense(pid, "SAME-BIZ", 5).getId());
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(ids).hasSize(8);
        assertThat(ids).containsOnly(ids.peek()); // 全部重放同一笔
        assertThat(service.listDispenses(pid)).hasSize(1);
        assertThat(service.getPrescription(pid).getDispensedQuantity()).isEqualTo(5);
        assertThat(stockOf()).isEqualTo(95);
    }

    @Test
    void cancel_and_dispense_race_has_single_outcome() throws Exception {
        long pid = newPrescription(100, 5, 20);
        stock(100);

        int dispensers = 16;
        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(dispensers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger dispensed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < dispensers; i++) {
            String biz = "RACE-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    service.dispense(pid, biz, 5);
                    dispensed.incrementAndGet();
                } catch (BusinessRuleException e) {
                    rejected.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        pool.submit(() -> {
            try {
                start.await();
                service.cancelPrescription(pid);
            } catch (BusinessRuleException e) {
                rejected.incrementAndGet();
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        var p = service.getPrescription(pid);
        if (p.getStatus() == PrescriptionStatus.CANCELLED) {
            // 作废成功：不允许任何调剂落库
            assertThat(dispensed.get()).isZero();
            assertThat(stockOf()).isEqualTo(100);
        } else {
            // 作废失败：每一笔成功调剂都必须是完整的，库存与账面一致
            assertThat(p.getDispensedQuantity()).isEqualTo((long) dispensed.get() * 5);
            assertThat(stockOf()).isEqualTo(100 - (long) dispensed.get() * 5);
        }
    }

    @Test
    void two_prescriptions_sharing_batches_never_oversell() throws Exception {
        long p1 = newPrescription(100, 100, 1);
        long p2 = newPrescription(100, 100, 1);
        stock(150); // 共享库存只够一笔

        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (long pid : new long[]{p1, p2}) {
            String biz = "SHARED-" + pid;
            pool.submit(() -> {
                try {
                    start.await();
                    service.dispense(pid, biz, 100);
                    success.incrementAndGet();
                } catch (BusinessRuleException e) {
                    // 库存不足方失败
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(success.get()).isEqualTo(1);
        assertThat(stockOf()).isEqualTo(50);
    }
}
