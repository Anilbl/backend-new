package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.Report;
import np.edu.nast.payroll.Payroll.reportdto.MonthlyPayrollDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT new np.edu.nast.payroll.Payroll.reportdto.MonthlyPayrollDTO(" +
            "FUNCTION('MONTHNAME', p.payDate), SUM(p.netSalary)) " +
            "FROM Payroll p " +
            "WHERE FUNCTION('YEAR', p.payDate) = :year " +
            "AND p.status = 'PAID' " +
            "AND p.isVoided = false " +
            "GROUP BY FUNCTION('MONTH', p.payDate), FUNCTION('MONTHNAME', p.payDate) " +
            "ORDER BY FUNCTION('MONTH', p.payDate)")
    List<MonthlyPayrollDTO> findMonthlyPaidPayroll(@Param("year") int year);

}