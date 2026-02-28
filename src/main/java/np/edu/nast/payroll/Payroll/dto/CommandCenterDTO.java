package np.edu.nast.payroll.Payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandCenterDTO {
    private Double monthlyPayrollTotal; // Sum of Net Salaries for the month
    private String payrollStatus;      // e.g., "Processing" or "Idle"
    private Integer compliancePercentage;  // Percentage of employees with finalized records
    private Integer pendingVerifications;  // Count of employees with status "READY" or "PENDING"
    private List<EmployeePayrollRowDTO> employeeRows; // The data for the main table
}