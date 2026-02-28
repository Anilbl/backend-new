package np.edu.nast.payroll.Payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePayrollRowDTO {
    private Integer empId;
    private String fullName;
    private Double basicSalary;      // Fixed salary from Employee Profile

    // --- Core 4 Components synchronized with PayrollServiceImpl logic ---
    private Double earnedSalary;       // Calculated: (Basic / DaysInMonth) * PaidDays
    private Double ssfContribution;    // Calculated: 11% of Earned Salary (if enrolled)
    private Double houseRentAllowance; // Default from SalaryComponent table
    private Double dearnessAllowance;  // Default from SalaryComponent table

    private Integer payrollId;       // Database ID if payroll is already saved
    private String status;           // "PAID", "PENDING_PAYMENT", "READY", or "NO_EARNINGS"
    private Boolean isActive;
    // In EmployeePayrollRowDTO.java
    private List<Map<String, Object>> extraComponents;// Current active status of the employee
}