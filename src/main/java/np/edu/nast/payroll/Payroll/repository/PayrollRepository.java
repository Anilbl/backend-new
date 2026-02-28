package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.dto.EmployeePayrollHistoryDTO;
import np.edu.nast.payroll.Payroll.entity.Payroll;
import np.edu.nast.payroll.Payroll.reportdto.DepartmentSummaryDTO;
import np.edu.nast.payroll.Payroll.reportdto.MonthlyPayrollDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Integer> {

    @Query("""
        SELECT 
            COALESCE(SUM(p.grossSalary), 0.0), 
            COALESCE(SUM(p.totalDeductions), 0.0), 
            COALESCE(SUM(p.netSalary), 0.0), 
            COALESCE(SUM(p.totalTax), 0.0), 
            COALESCE(SUM(p.ssfContribution), 0.0), 
            COALESCE(SUM(p.overtimePay), 0.0), 
            COUNT(p)
        FROM Payroll p 
        WHERE YEAR(p.payPeriodStart) = :year 
        AND MONTH(p.payPeriodStart) = :month 
        AND p.status = 'PAID' 
        AND p.isVoided = false
    """)
    Object getOverallMetrics(@Param("month") int month, @Param("year") int year);

    @Query("""
        SELECT new np.edu.nast.payroll.Payroll.reportdto.DepartmentSummaryDTO(
            d.deptName, 
            (SELECT COUNT(e) FROM Employee e WHERE e.department.deptId = d.deptId),
            COUNT(p.payrollId), 
            COALESCE(SUM(p.netSalary), 0.0), 
            COALESCE(SUM(p.totalTax), 0.0)
        )
        FROM Department d
        LEFT JOIN Employee emp ON emp.department.deptId = d.deptId
        LEFT JOIN Payroll p ON p.employee.empId = emp.empId 
            AND YEAR(p.payPeriodStart) = :year 
            AND MONTH(p.payPeriodStart) = :month 
            AND p.status = 'PAID' 
            AND p.isVoided = false
        GROUP BY d.deptId, d.deptName
    """)
    List<DepartmentSummaryDTO> getDepartmentalSummary(@Param("month") int month, @Param("year") int year);

    @Query("SELECT p FROM Payroll p WHERE p.employee.empId = :empId AND p.status != 'VOIDED' ORDER BY p.payPeriodStart DESC")
    List<Payroll> findHistoryByEmployeeId(@Param("empId") Integer empId);

    List<Payroll> findByEmployeeEmpId(Integer empId);

    @Query("SELECT COALESCE(SUM(p.netSalary), 0) FROM Payroll p WHERE YEAR(p.payDate) = :year AND p.status = 'PAID'")
    double yearlyPayroll(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(p.totalDeductions), 0) FROM Payroll p WHERE YEAR(p.payDate) = :year AND p.status != 'VOIDED'")
    double yearlyDeductions(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(p.totalAllowances), 0) FROM Payroll p WHERE YEAR(p.payDate) = :year AND p.status != 'VOIDED'")
    double yearlyAllowances(@Param("year") int year);

    // This method shows all non-voided payroll (History/General Analytics)
    @Query("""
        SELECT new np.edu.nast.payroll.Payroll.reportdto.MonthlyPayrollDTO(
            FUNCTION('MONTHNAME', p.payDate), SUM(p.netSalary)
        )
        FROM Payroll p
        WHERE FUNCTION('YEAR', p.payDate) = :year AND p.status != 'VOIDED'
        GROUP BY FUNCTION('MONTH', p.payDate), FUNCTION('MONTHNAME', p.payDate)
        ORDER BY FUNCTION('MONTH', p.payDate)
    """)
    List<MonthlyPayrollDTO> monthlyPayroll(@Param("year") int year);

    /**
     * DYNAMIC GRAPH FIX:
     * This method strictly filters by 'PAID' and uses the correct field 'payDate'
     */
    @Query("""
        SELECT new np.edu.nast.payroll.Payroll.reportdto.MonthlyPayrollDTO(
            FUNCTION('MONTHNAME', p.payDate), SUM(p.netSalary)
        )
        FROM Payroll p
        WHERE FUNCTION('YEAR', p.payDate) = :year 
        AND p.status = 'PAID' 
        AND p.isVoided = false
        GROUP BY FUNCTION('MONTH', p.payDate), FUNCTION('MONTHNAME', p.payDate)
        ORDER BY FUNCTION('MONTH', p.payDate)
    """)
    List<MonthlyPayrollDTO> findMonthlyPaidPayroll(@Param("year") int year);

    @Query("SELECT p FROM Payroll p WHERE p.employee.empId = :empId AND YEAR(p.payDate) = :year AND MONTH(p.payDate) = :month")
    Optional<Payroll> findByEmployeeEmpIdAndMonth(@Param("empId") Integer empId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT p FROM Payroll p WHERE p.employee.empId = :empId " +
            "AND p.payPeriodStart = :startDate AND p.payPeriodEnd = :endDate " +
            "AND p.status != 'VOIDED'")
    Optional<Payroll> findByEmployeeAndPeriod(
            @Param("empId") Integer empId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    List<Payroll> findByPayPeriodStart(LocalDate payPeriodStart);
}