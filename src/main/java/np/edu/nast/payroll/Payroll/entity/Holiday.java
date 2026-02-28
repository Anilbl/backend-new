package np.edu.nast.payroll.Payroll.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "holiday", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"holiday_date"})
})
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holiday_id")
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false) // Added for user-friendly error messages
    private String holidayName;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "holiday_type", nullable = false)
    private String holidayType; // e.g., "NATIONAL", "PUBLIC", "FESTIVAL"
}