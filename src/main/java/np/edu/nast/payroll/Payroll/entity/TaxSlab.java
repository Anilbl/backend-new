package np.edu.nast.payroll.Payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;

@Entity
@Table(name = "tax_slab")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TaxSlab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer taxSlabId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double minAmount;

    @Column(nullable = false)
    private Double maxAmount;
    @Column(nullable = false)
    private Double previousLimit;
    @Column(nullable = false)
    private Double ratePercentage;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String taxpayerStatus;
    // Single or Couple status

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @CreatedBy
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @LastModifiedBy
    private User updatedBy;
}