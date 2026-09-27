package com.chris64233.pharmacydispense.repository;

import com.chris64233.pharmacydispense.domain.ReturnRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReturnRecordRepository extends JpaRepository<ReturnRecord, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ReturnRecord> findByBizNo(String bizNo);

    /** 退药链：一张调剂记录对应的所有退药，按时间顺序。 */
    @EntityGraph(attributePaths = "items")
    List<ReturnRecord> findByDispenseIdOrderByIdAsc(Long dispenseId);
}
