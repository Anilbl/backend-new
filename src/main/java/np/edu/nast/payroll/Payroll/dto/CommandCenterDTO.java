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
    private double monthlyPayrollTotal;
    private String payrollStatus;      // e.g., "Processing" or "Idle"
    private int compliancePercentage;  // e.g., 100
    private int pendingVerifications;  // e.g., count of "READY" employees
    private List<EmployeePayrollRowDTO> employeeRows;
}