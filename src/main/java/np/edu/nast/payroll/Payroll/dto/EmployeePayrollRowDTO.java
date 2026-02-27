package np.edu.nast.payroll.Payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePayrollRowDTO {
    private Integer empId;
    private String fullName;
    private Double basicSalary;      // The fixed salary in the employee profile
    private Double earnedSalary;     // The calculated earned salary for the month
    private Integer payrollId;       // Present if already saved in DB

    // Manual Adjustment Fields
    private Double festivalBonus;
    private Double bonuses;          // Matches frontend 'bonuses' key
    private Double citContribution;

    private String status;           // "PAID", "PENDING_PAYMENT", "READY", or "NO_EARNINGS"

    // Status Field
    private Boolean isActive;        // To represent the employee's current active status
}