package np.edu.nast.payroll.Payroll.service;

import np.edu.nast.payroll.Payroll.dto.PasswordChangeDTO;
import np.edu.nast.payroll.Payroll.entity.Employee;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface EmployeeService {
    Employee create(Employee employee);
    Employee update(Integer id, Employee employee);
    void delete(Integer id);
    void changePassword(Integer id, PasswordChangeDTO dto);
    void updatePhotoUrl(Integer id, String photoUrl);

    Employee getById(Integer id);
    Employee getByEmail(String email);

    // NEW FEATURE: Concept change to User ID
    Employee getByUserId(Integer userId);
    String updateProfilePhoto(Integer empId, MultipartFile file);
    void updateEmailPreference(Integer empId, Boolean preference);
    void updatePassword(Integer empId, String currentPassword, String newPassword);
    List<Employee> getAll();
    Map<Integer, Long> getActiveEmployeeStats();
    Map<String, Object> getDashboardStats(Integer id);
}