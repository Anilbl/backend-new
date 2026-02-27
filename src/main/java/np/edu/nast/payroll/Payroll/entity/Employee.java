package np.edu.nast.payroll.Payroll.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id")
    @JsonProperty("empId")
    private Integer empId;

    @OneToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = true)
    @JsonIgnore
    private User user;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "middle_name") // New Column
    private String middleName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "gender", nullable = false) // New Column
    private String gender;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<BankAccount> bankAccount;

    public BankAccount getPrimaryBankAccount() {
        if (bankAccount == null || bankAccount.isEmpty()) return null;
        return bankAccount.stream()
                .filter(BankAccount::getIsPrimary)
                .findFirst()
                .orElse(bankAccount.get(0));
    }

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 10, nullable = false)
    @Size(min = 10, max = 10, message = "Contact number must be exactly 10 digits")
    private String contact;

    @Column(name = "marital_status", nullable = false)
    private String maritalStatus;

    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "position_id", nullable = false)
    @JsonIgnoreProperties("employees")
    private Designation position;

    @Column(nullable = false)
    private String education;

    @Column(name = "employment_status", nullable = false)
    private String employmentStatus;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(nullable = false)
    private String address;

    @Column(name = "basic_salary", nullable = false)
    private Double basicSalary;

    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "dept_id", nullable = false)
    @JsonIgnoreProperties("employees")
    private Department department;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "email_notifications")
    private Boolean emailNotifications = false;

    @Column(name = "photo_url", nullable = false)
    private String photoUrl;

    @Column(name = "is_ssf_enrolled", nullable = false)
    private Boolean isSsfEnrolled = false; // Default to false

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isActive == null) this.isActive = true;
        if (this.maritalStatus == null) this.maritalStatus = "SINGLE";
        if (this.employmentStatus == null) this.employmentStatus = "FULL_TIME";
        if (this.basicSalary == null) this.basicSalary = 0.0;
        // Default gender if not provided
        if (this.gender == null) this.gender = "OTHER";
    }
}