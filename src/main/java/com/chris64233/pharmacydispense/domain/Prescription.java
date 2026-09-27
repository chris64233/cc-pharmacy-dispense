package com.chris64233.pharmacydispense.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.chris64233.pharmacydispense.error.BusinessRuleException;

/**
 * 处方：记录患者、药品、总剂量、单次调剂上限、有效期和允许调剂次数，
 * 并维护累计调剂量与已调剂次数（退药恢复余额时只回退累计量，次数不回退）。
 */
@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 处方号，业务唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String prescriptionNo;

    @Column(nullable = false, length = 64)
    private String patientId;

    @Column(nullable = false, length = 64)
    private String patientName;

    @Column(nullable = false, length = 64)
    private String drugCode;

    @Column(nullable = false, length = 128)
    private String drugName;

    /** 处方总剂量 */
    @Column(nullable = false)
    private long totalQuantity;

    /** 单次调剂上限 */
    @Column(nullable = false)
    private long perDoseLimit;

    /** 允许调剂次数 */
    @Column(nullable = false)
    private int allowedDispenseCount;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validUntil;

    /** 累计已调剂剂量（退药时回退） */
    @Column(nullable = false)
    private long dispensedQuantity;

    /** 已调剂次数（退药不回退，次数一旦使用即被消耗） */
    @Column(nullable = false)
    private int dispensedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PrescriptionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 乐观锁，与处方行悲观锁共同保证并发安全 */
    @Version
    private long version;

    protected Prescription() {
    }

    public Prescription(String prescriptionNo, String patientId, String patientName,
                        String drugCode, String drugName, long totalQuantity,
                        long perDoseLimit, int allowedDispenseCount,
                        LocalDate validFrom, LocalDate validUntil, LocalDateTime createdAt) {
        this.prescriptionNo = prescriptionNo;
        this.patientId = patientId;
        this.patientName = patientName;
        this.drugCode = drugCode;
        this.drugName = drugName;
        this.totalQuantity = totalQuantity;
        this.perDoseLimit = perDoseLimit;
        this.allowedDispenseCount = allowedDispenseCount;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.dispensedQuantity = 0;
        this.dispensedCount = 0;
        this.status = PrescriptionStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public long remainingQuantity() {
        return totalQuantity - dispensedQuantity;
    }

    public boolean canUseCount() {
        return dispensedCount < allowedDispenseCount;
    }

    /**
     * 登记一次完整调剂：累计量、次数在同一处方行上推进。
     */
    public void registerDispense(long quantity) {
        this.dispensedQuantity += quantity;
        this.dispensedCount += 1;
    }

    /**
     * 退药恢复处方余额，但不恢复调剂次数。
     */
    public void restoreOnReturn(long quantity) {
        if (quantity > dispensedQuantity) {
            throw new IllegalStateException("退药量超过累计调剂量");
        }
        this.dispensedQuantity -= quantity;
    }

    public boolean isVoid() {
        return status == PrescriptionStatus.VOID;
    }

    /**
     * 作废：只有从未产生过完整调剂的处方可以作废，保证“作废或完整调剂”二选一。
     */
    public void voidPrescription() {
        if (status == PrescriptionStatus.VOID) {
            return;
        }
        if (dispensedCount > 0) {
            throw new BusinessRuleException("处方已产生调剂，不能作废");
        }
        this.status = PrescriptionStatus.VOID;
    }

    public Long getId() {
        return id;
    }

    public String getPrescriptionNo() {
        return prescriptionNo;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public String getDrugName() {
        return drugName;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public long getPerDoseLimit() {
        return perDoseLimit;
    }

    public int getAllowedDispenseCount() {
        return allowedDispenseCount;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public long getDispensedQuantity() {
        return dispensedQuantity;
    }

    public int getDispensedCount() {
        return dispensedCount;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
