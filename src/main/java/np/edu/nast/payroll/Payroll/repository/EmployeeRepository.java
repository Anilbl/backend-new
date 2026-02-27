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

    // Add this line to resolve the error
    List<Employee> findByIsActiveTrue();
    // Custom query to only fetch active employees
    @Query("SELECT e FROM Employee e WHERE e.isActive = true")
    List<Employee> findAllActive();
    /**
     * Analytical query for employee growth chart.
     */
    @Query("""
        SELECT FUNCTION('MONTH', e.joiningDate), COUNT(e)
        FROM Employee e
        WHERE e.isActive = true
        GROUP BY FUNCTION('MONTH', e.joiningDate)
    """)
    List<Object[]> countActiveEmployeesPerMonth();

    /**
     * Basic exists check for validation.
     */
    boolean existsByEmail(String email);

    /**
     * Standard find by email used in AuthServiceImpl to link User to Employee.
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Look up employee via the nested User entity's email.
     */
    Optional<Employee> findByUser_Email(String email);

    /**
     * Explicit Query to find employee by User ID.
     */
    @Query("SELECT e FROM Employee e WHERE e.user.userId = :userId")
    Optional<Employee> findByUser_UserId(@Param("userId") Integer userId);

    /**
     * Look up employee via the nested User entity's username.
     */
    Optional<Employee> findByUser_Username(String username);

    long countByIsActiveTrue();
    /**
     * Search functionality for the Admin Employee list.
     */
    @Query("""
        SELECT e FROM Employee e
        WHERE CAST(e.empId AS string) = :query
           OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    List<Employee> searchByIdOrName(@Param("query") String query);
}