package np.edu.nast.payroll.Payroll.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePayrollHistoryDTO {
    private Integer payrollId; // Changed from Long to Integer
    private LocalDate payPeriodStart;
    private LocalDate payDate;
    private Double grossSalary;
    private Double ssfContribution;
    private Double totalTax;
    private Double netSalary;
    private String status;
}