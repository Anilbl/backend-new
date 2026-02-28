package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Integer> {

    /**
     * CRITICAL FOR ATTENDANCE:
     * Checks if today falls within an approved leave range for a specific employee.
     */
    boolean existsByEmployee_EmpIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer empId, String status, LocalDate todayForStart, LocalDate todayForEnd
    );

    /**
     * USED FOR PAYROLL & ATTENDANCE STATS:
     * Finds leaves that overlap with the current month/period.
     */
    @Query("SELECT l FROM EmployeeLeave l WHERE l.employee.empId = :empId " +
            "AND l.status = :status " +
            "AND l.startDate <= :periodEnd " +
            "AND l.endDate >= :periodStart")
    List<EmployeeLeave> findRelevantLeaves(
            @Param("empId") Integer empId,
            @Param("status") String status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /**
     * ADMIN LEAVE FILTERING:
     * Powers the "Manage Leaves" table with search and status filters.
     */
    @Query("SELECT l FROM EmployeeLeave l WHERE " +
            "YEAR(l.startDate) = :year AND " +
            "MONTH(l.startDate) = :month AND " +
            "(:status = 'All' OR l.status = :status) AND " +
            "(LOWER(l.employee.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(l.employee.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " CAST(l.employee.empId AS string) LIKE CONCAT('%', :search, '%'))")
    List<EmployeeLeave> findFilteredLeaves(
            @Param("year") int year,
            @Param("month") int month,
            @Param("status") String status,
            @Param("search") String search
    );

    /**
     * Fix for "Cannot resolve method 'findAllByEmployee'"
     * This version takes the full Employee entity object.
     */
    List<EmployeeLeave> findAllByEmployee(Employee employee);

    /**
     * Standard lookup by Employee ID.
     */
    List<EmployeeLeave> findByEmployee_EmpId(Integer empId);

    long countByEmployeeEmpIdAndStatus(Integer empId, String status);

    long countByStatus(String status);
}