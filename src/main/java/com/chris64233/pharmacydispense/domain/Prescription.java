package com.chris64233.pharmacydispense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 处方：记录患者、药品、总剂量、单次上限、有效期与允许调剂次数，
 * 并跟踪累计已调剂量与已调剂次数。
 */
@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String drugCode;

    @Column(nullable = false)
    private String drugName;

    /** 处方总剂量 */
    @Column(nullable = false)
    private long totalQuantity;

    /** 单次调剂上限 */
    @Column(nullable = false)
    private long maxPerDispense;

    /** 有效期起（含） */
    @Column(nullable = false)
    private LocalDate validFrom;

    /** 有效期止（含） */
    @Column(nullable = false)
    private LocalDate validTo;

    /** 允许调剂次数 */
    @Column(nullable = false)
    private int allowedDispenseCount;

    /** 累计已调剂量（退药会回补） */
    @Column(nullable = false)
    private long dispensedQuantity;

    /** 累计已调剂次数（退药会回补） */
    @Column(nullable = false)
    private int dispensedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrescriptionStatus status = PrescriptionStatus.ACTIVE;

    protected Prescription() {
    }

    public Prescription(String patientName, String drugCode, String drugName,
                        long totalQuantity, long maxPerDispense,
                        LocalDate validFrom, LocalDate validTo, int allowedDispenseCount) {
        this.patientName = patientName;
        this.drugCode = drugCode;
        this.drugName = drugName;
        this.totalQuantity = totalQuantity;
        this.maxPerDispense = maxPerDispense;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.allowedDispenseCount = allowedDispenseCount;
        this.dispensedQuantity = 0;
        this.dispensedCount = 0;
        this.status = PrescriptionStatus.ACTIVE;
    }

    public long remainingQuantity() {
        return totalQuantity - dispensedQuantity;
    }

    public int remainingCount() {
        return allowedDispenseCount - dispensedCount;
    }

    public Long getId() {
        return id;
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

    public long getMaxPerDispense() {
        return maxPerDispense;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public int getAllowedDispenseCount() {
        return allowedDispenseCount;
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

    /** 以下方法仅供领域服务在持有行锁的事务内调用。 */

    public void addDispense(long quantity) {
        this.dispensedQuantity += quantity;
        this.dispensedCount += 1;
        if (remainingQuantity() == 0 || remainingCount() == 0) {
            this.status = PrescriptionStatus.COMPLETED;
        }
    }

    /**
     * 退药回补。剂量每次退药都回补；次数只在该次调剂被“全额退清”时回补一次，
     * 否则一次调剂的多次部分退药会把次数扣成负数。
     */
    public void revertDispense(long quantity, boolean fullyReturned) {
        this.dispensedQuantity -= quantity;
        if (fullyReturned) {
            this.dispensedCount -= 1;
        }
        if (this.status == PrescriptionStatus.COMPLETED && remainingQuantity() > 0 && remainingCount() > 0) {
            this.status = PrescriptionStatus.ACTIVE;
        }
    }

    public void cancel() {
        this.status = PrescriptionStatus.CANCELLED;
    }
}
