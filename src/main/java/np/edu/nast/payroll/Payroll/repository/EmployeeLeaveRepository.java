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
     * NEW: Dynamic filtering for the Admin Leave Page.
     * Searches by Year, Month, Status (with "All" support), and Search Term (Name/ID).
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
     * NEW: Checks if an employee is currently on an approved leave.
     * Logic: startDate <= today AND endDate >= today
     */
    boolean existsByEmployee_EmpIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer empId, String status, LocalDate todayForStart, LocalDate todayForEnd
    );

    List<EmployeeLeave> findByEmployeeEmpIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer empId, String status, LocalDate periodEnd, LocalDate periodStart
    );

    List<EmployeeLeave> findByEmployee_EmpId(Integer empId);
    List<EmployeeLeave> findAllByEmployee(Employee employee);
    long countByEmployeeEmpIdAndStatus(Integer empId, String status);
    long countByStatus(String status);
}