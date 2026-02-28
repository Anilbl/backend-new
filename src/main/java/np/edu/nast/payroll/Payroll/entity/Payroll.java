package np.edu.nast.payroll.Payroll.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payroll", indexes = {
        @Index(name = "idx_payroll_emp", columnList = "emp_id"),
        @Index(name = "idx_payroll_pay_date", columnList = "pay_date"),
        @Index(name = "idx_payroll_pay_group", columnList = "pay_group_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_id")
    private Integer payrollId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "processed_by", nullable = false)
    private User processedBy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pay_group_id", nullable = false)
    private PayGroup payGroup;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "monthly_info_id", nullable = false)
    private MonthlyInfo monthlyInfo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "payment_account_id", nullable = false)
    private BankAccount paymentAccount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    // --- EXPLICIT COLUMN MAPPINGS TO MATCH DB ---
    @Column(name = "basic_salary", nullable = false)
    @Builder.Default
    private Double basicSalary = 0.0;

    @Column(name = "total_allowances")
    @Builder.Default
    private Double totalAllowances = 0.0;

    @Column(name = "festival_bonus")
    @Builder.Default
    private Double festivalBonus = 0.0;

    @Column(name = "other_bonuses")
    @Builder.Default
    private Double otherBonuses = 0.0;

    @Column(name = "overtime_pay")
    @Builder.Default
    private Double overtimePay = 0.0;

    @Column(name = "ssf_contribution")
    @Builder.Default
    private Double ssfContribution = 0.0;

    @Column(name = "cit_contribution")
    @Builder.Default
    private Double citContribution = 0.0;

    @Column(name = "insurance_deduction")
    @Builder.Default
    private Double insuranceDeduction = 0.0;

    @Column(name = "gross_salary", nullable = false)
    @Builder.Default
    private Double grossSalary = 0.0;

    @Column(name = "taxable_income", nullable = false)
    @Builder.Default
    private Double taxableIncome = 0.0;

    @Column(name = "total_tax", nullable = false)
    @Builder.Default
    private Double totalTax = 0.0;

    @Column(name = "total_deductions", nullable = false)
    @Builder.Default
    private Double totalDeductions = 0.0;

    @Column(name = "net_salary", nullable = false)
    @Builder.Default
    private Double netSalary = 0.0;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payslip_ref", unique = true)
    private String payslipRef;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "is_voided")
    @Builder.Default
    private Boolean isVoided = false;

    @Column(name = "currency_code")
    private String currencyCode;
    @Column(name = "pay_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    @Column(name = "pay_period_start")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end")
    private LocalDate payPeriodEnd;

    @Builder.Default
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt = LocalDateTime.now();
    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PayrollAudit> auditLogs;

    // 1. Add this field to your existing fields
    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PayrollExtraComponent> extraComponents = new ArrayList<>();

    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayoutInfo> payouts = new ArrayList<>();

    // 2. Add this helper method inside the class body
    public void addExtraComponent(String name, Double amount, String type, String calcType, String desc) {
        if (this.extraComponents == null) this.extraComponents = new ArrayList<>();
        PayrollExtraComponent component = PayrollExtraComponent.builder()
                .payroll(this)
                .componentName(name)
                .amount(amount)
                .type(type)
                .calculationType(calcType)
                .description(desc)
                .build();
        this.extraComponents.add(component);
    }

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) this.processedAt = LocalDateTime.now();
        if (this.isVoided == null) this.isVoided = false;
        if (this.currencyCode == null) this.currencyCode = "NPR";
    }
}