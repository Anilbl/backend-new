package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.LeaveBalance;
import np.edu.nast.payroll.Payroll.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Integer> {

    @Query("SELECT SUM(b.currentBalanceDays) FROM LeaveBalance b WHERE b.employee.empId = :empId")
    Integer sumCurrentBalanceByEmployeeId(@Param("empId") Integer empId);
    /**
     * Finds all leave balances for a specific employee.
     * Used to display the "Available Quota" in the Employee Portal.
     */
    List<LeaveBalance> findByEmployeeEmpId(Integer empId);

    /**
     * CRITICAL: Finds the specific balance record for a deduction or refund.
     * Links the Employee, the Type of Leave, and the specific Year.
     */
    Optional<LeaveBalance> findByEmployeeAndLeaveTypeAndYear(Employee employee, LeaveType leaveType, Integer year);
    long countByCurrentBalanceDaysGreaterThan(double days);
    /**
     * Dashboard Query: Counts how many employees currently have
     * remaining leave days of any type.
     */
    @Query("""
        SELECT COUNT(DISTINCT lb.employee.empId)
        FROM LeaveBalance lb
        WHERE lb.currentBalanceDays > :days
    """)
    long countEmployeesWithRemainingBalance(@Param("days") double days);

    /**
     * Finds balances by employee and year.
     * Useful for year-end salary (encashment) calculations.
     */
    List<LeaveBalance> findByEmployeeEmpIdAndYear(Integer empId, Integer year);
}