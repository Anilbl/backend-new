package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    /**
     * Standard derived method to fetch active employees for dropdowns or simple lists.
     */
    List<Employee> findByIsActiveTrue();

    /**
     * Custom query used by the Payroll engine to ensure only currently
     * employed staff are processed for salary.
     */
    @Query("SELECT e FROM Employee e WHERE e.isActive = true")
    List<Employee> findAllActive();

    /**
     * CRITICAL FOR ATTENDANCE:
     * Identifies the Employee record associated with a specific User login ID.
     */
    @Query("SELECT e FROM Employee e WHERE e.user.userId = :userId")
    Optional<Employee> findByUser_UserId(@Param("userId") Integer userId);

    /**
     * Analytical query for employee growth chart (Admin Dashboard).
     * Groups active employees by their joining month.
     */
    @Query("""
        SELECT FUNCTION('MONTH', e.joiningDate), COUNT(e)
        FROM Employee e
        WHERE e.isActive = true
        GROUP BY FUNCTION('MONTH', e.joiningDate)
    """)
    List<Object[]> countActiveEmployeesPerMonth();

    /**
     * Search functionality for the Admin Employee list.
     * Matches against ID (as String) or Name (Case-Insensitive).
     */
    @Query("""
        SELECT e FROM Employee e
        WHERE CAST(e.empId AS string) = :query
           OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    List<Employee> searchByIdOrName(@Param("query") String query);

    /**
     * AUTHENTICATION & LINKING HELPERS
     */
    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Employee> findByUser_Email(String email);

    Optional<Employee> findByUser_Username(String username);


    /**
     * Statistics for Dashboard Cards
     */
    long countByIsActiveTrue();
}