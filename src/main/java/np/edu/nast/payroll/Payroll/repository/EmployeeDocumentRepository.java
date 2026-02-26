package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Integer> {
    List<EmployeeDocument> findByStatus(String status);                    // ✅ admin filter
    List<EmployeeDocument> findByEmployee_EmpId(Integer empId);           // ✅ submit for review
}