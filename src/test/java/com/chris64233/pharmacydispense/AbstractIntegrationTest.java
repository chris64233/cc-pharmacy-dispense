package com.chris64233.pharmacydispense;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.domain.InventoryBatchRepository;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.domain.StockMovementRepository;
import com.chris64233.pharmacydispense.service.InventoryService;
import com.chris64233.pharmacydispense.service.PrescriptionService;

import jakarta.persistence.EntityManager;

/**
 * 测试基类：每个用例前清空全部业务表，并提供建处方/入库的便捷方法。
 */
@SpringBootTest(classes = {PharmacyDispenseApplication.class, FixedClockConfig.class})
public abstract class AbstractIntegrationTest {

    protected static final LocalDate TODAY = FixedClockConfig.TODAY;

    @Autowired
    protected PrescriptionService prescriptionService;

    @Autowired
    protected InventoryService inventoryService;

    @Autowired
    protected PrescriptionRepository prescriptionRepository;

    @Autowired
    protected InventoryBatchRepository batchRepository;

    @Autowired
    protected StockMovementRepository movementRepository;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        // 编程式事务：每个用例前在独立事务中清空业务表（不能用类级 @Transactional，
        // 并发测试需要 setup 数据已提交，对工作线程可见）
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from return_line").executeUpdate();
            entityManager.createNativeQuery("delete from return_record").executeUpdate();
            entityManager.createNativeQuery("delete from dispense_line").executeUpdate();
            entityManager.createNativeQuery("delete from dispense_record").executeUpdate();
            entityManager.createNativeQuery("delete from stock_movement").executeUpdate();
            entityManager.createNativeQuery("delete from inventory_batch").executeUpdate();
            entityManager.createNativeQuery("delete from prescription").executeUpdate();
        });
    }

    protected Prescription createPrescription(String no, String drug, long total,
                                              long perDose, int times) {
        return prescriptionService.create(no, "P-001", "张三", drug, "测试药品",
                total, perDose, times, TODAY.minusDays(1), TODAY.plusDays(30));
    }

    protected InventoryBatch inbound(String drug, String batchNo, long qty,
                                     LocalDate expiry) {
        return inventoryService.inbound(drug, batchNo, qty, expiry, "IN-" + batchNo);
    }

    protected long batchQuantity(String drug, String batchNo) {
        return batchRepository.findByDrugCodeAndBatchNo(drug, batchNo).orElseThrow()
                .getQuantity();
    }

    protected List<InventoryBatch> batches(String drug) {
        return inventoryService.listBatches(drug);
    }
}
