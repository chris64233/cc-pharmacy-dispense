package com.chris64233.pharmacydispense.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.error.BusinessRuleException;

/**
 * FEFO（先到期先出）批次分配：在已加行锁的批次集合上计算扣减方案。
 * 方案计算阶段不修改任何库存；库存不足直接抛错，由调用方整体回滚。
 */
public final class BatchAllocator {

    private BatchAllocator() {
    }

    public record AllocationLine(InventoryBatch batch, long quantity) {
    }

    /**
     * 按到期日升序（同日按批次 id）贪心分配 requestedQuantity。
     *
     * @param today 业务日期：到期日早于该日的批次视为失效，不参与分配
     */
    public static List<AllocationLine> allocateFefo(List<InventoryBatch> lockedBatches,
                                                    LocalDate today,
                                                    long requestedQuantity) {
        List<InventoryBatch> eligible = lockedBatches.stream()
                .filter(b -> b.getStatus() == BatchStatus.ACTIVE)
                .filter(b -> !b.getExpiryDate().isBefore(today))
                .sorted(Comparator.comparing(InventoryBatch::getExpiryDate)
                        .thenComparing(InventoryBatch::getId))
                .toList();

        long available = eligible.stream().mapToLong(InventoryBatch::getQuantity).sum();
        if (available < requestedQuantity) {
            throw new BusinessRuleException(
                    "合格批次库存不足：需要 " + requestedQuantity + "，可用 " + available);
        }

        List<AllocationLine> plan = new ArrayList<>();
        long remaining = requestedQuantity;
        for (InventoryBatch batch : eligible) {
            if (remaining == 0) {
                break;
            }
            long take = Math.min(batch.getQuantity(), remaining);
            if (take > 0) {
                plan.add(new AllocationLine(batch, take));
                remaining -= take;
            }
        }
        return plan;
    }
}
