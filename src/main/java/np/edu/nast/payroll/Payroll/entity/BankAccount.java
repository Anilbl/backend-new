package np.edu.nast.payroll.Payroll.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer accountId;

    // Employee must exist before bank account creation
    @ManyToOne(optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    @JsonIgnore
    private Employee employee;

    // Bank must exist before bank account creation
    @ManyToOne(optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "account_number", nullable = false, unique = true)
    private Long accountNumber;

    @Column(nullable = false)
    private String accountType; // Savings / Current / Salary

    @Column(nullable = false)
    private Boolean isPrimary;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Automatically set createdAt before insert
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
