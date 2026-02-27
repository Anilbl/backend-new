package np.edu.nast.payroll.Payroll.service.impl;

import np.edu.nast.payroll.Payroll.dto.PasswordChangeDTO;
import np.edu.nast.payroll.Payroll.entity.*;
import np.edu.nast.payroll.Payroll.reportdto.AttendanceSummaryDTO;
import np.edu.nast.payroll.Payroll.repository.*;
import np.edu.nast.payroll.Payroll.service.EmployeeService;
import np.edu.nast.payroll.Payroll.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final DesignationRepository designationRepo;
    private final UserRepository userRepo;
    private final AttendanceRepository attendanceRepo;
    private final BankRepository bankRepo;
    private final BankAccountRepository bankAccountRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public EmployeeServiceImpl(EmployeeRepository employeeRepo,
                               DepartmentRepository departmentRepo,
                               DesignationRepository designationRepo,
                               UserRepository userRepo,
                               AttendanceRepository attendanceRepo,
                               BankRepository bankRepo,
                               BankAccountRepository bankAccountRepo) {
        this.employeeRepo = employeeRepo;
        this.departmentRepo = departmentRepo;
        this.designationRepo = designationRepo;
        this.userRepo = userRepo;
        this.attendanceRepo = attendanceRepo;
        this.bankRepo = bankRepo;
        this.bankAccountRepo = bankAccountRepo;
    }

    // --- CORE CRUD OPERATIONS ---

    @Override
    public Employee create(Employee employee) {
        // 1. UNIQUE EMAIL RELEASE LOGIC
        employeeRepo.findByEmail(employee.getEmail()).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getIsActive())) {
                throw new EmailAlreadyExistsException("An active employee already exists with email: " + employee.getEmail());
            } else {
                String releaseTag = "_rel_" + System.currentTimeMillis();
                existing.setEmail(existing.getEmail() + releaseTag);
                employeeRepo.saveAndFlush(existing);
            }
        });

        User associatedUser = userRepo.findByEmailIgnoreCase(employee.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + employee.getEmail()));

        if (employeeRepo.findByUser_UserId(associatedUser.getUserId()).isPresent()) {
            throw new RuntimeException("This user is already registered as an employee.");
        }

        employee.setUser(associatedUser);
        employee.setIsActive(true);

        // Handle SSF Enrollment flag from frontend
        if (employee.getIsSsfEnrolled() == null) {
            employee.setIsSsfEnrolled(false);
        }

        validateAndAttachForeignKeys(employee);

        List<BankAccount> incomingBankAccounts = employee.getBankAccount();
        employee.setBankAccount(null);

        Employee savedEmployee = employeeRepo.save(employee);

        if (incomingBankAccounts != null && !incomingBankAccounts.isEmpty()) {
            saveBankAccount(savedEmployee, incomingBankAccounts.get(0));
        }
        return savedEmployee;
    }

    @Override
    public Employee update(Integer id, Employee employee) {
        Employee existing = employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (employee.getEmail() != null && !employee.getEmail().equalsIgnoreCase(existing.getEmail())) {
            employeeRepo.findByEmail(employee.getEmail()).ifPresent(other -> {
                if (Boolean.TRUE.equals(other.getIsActive())) {
                    throw new EmailAlreadyExistsException("Email already taken by another active employee.");
                } else {
                    other.setEmail(other.getEmail() + "_rel_" + System.currentTimeMillis());
                    employeeRepo.saveAndFlush(other);
                }
            });

            User newUser = userRepo.findByEmailIgnoreCase(employee.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("No User found for the new email."));
            existing.setUser(newUser);
            existing.setEmail(employee.getEmail());
        }

        validateAndAttachForeignKeys(employee);

        existing.setFirstName(employee.getFirstName());
        existing.setMiddleName(employee.getMiddleName());
        existing.setLastName(employee.getLastName());
        existing.setGender(employee.getGender());
        existing.setContact(employee.getContact());
        existing.setMaritalStatus(employee.getMaritalStatus());
        existing.setEducation(employee.getEducation());
        existing.setEmploymentStatus(employee.getEmploymentStatus());
        existing.setJoiningDate(employee.getJoiningDate());
        existing.setAddress(employee.getAddress());
        existing.setIsActive(employee.getIsActive());
        existing.setBasicSalary(employee.getBasicSalary());
        existing.setDepartment(employee.getDepartment());
        existing.setPosition(employee.getPosition());
        existing.setPayGroup(employee.getPayGroup());

        // Update SSF Enrollment flag
        if (employee.getIsSsfEnrolled() != null) {
            existing.setIsSsfEnrolled(employee.getIsSsfEnrolled());
        }

        if (existing.getUser() != null) existing.getUser().setEmail(existing.getEmail());

        if (employee.getBankAccount() != null && !employee.getBankAccount().isEmpty()) {
            saveBankAccount(existing, employee.getBankAccount().get(0));
        }
        return employeeRepo.save(existing);
    }

    @Override
    public void delete(Integer id) {
        Employee employee = employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        employee.setIsActive(false);
        String timestamp = "_del_" + System.currentTimeMillis();

        employee.setEmail(employee.getEmail() + timestamp);

        if (employee.getUser() != null) {
            User user = employee.getUser();
            user.setIsActive(false);
            user.setEmail(user.getEmail() + timestamp);
            user.setUsername(user.getUsername() + timestamp);
            userRepo.save(user);
        }
        employeeRepo.save(employee);
    }

    @Override
    public List<Employee> getAll() {
        return employeeRepo.findByIsActiveTrue();
    }

    @Override
    public Employee getById(Integer id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    // --- SETTINGS & PROFILE FEATURES ---

    @Override
    public String updateProfilePhoto(Integer empId, MultipartFile file) {
        Employee emp = employeeRepo.findById(empId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        try {
            String uploadDir = "uploads/photos/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String fileName = "emp_" + empId + "_" + System.currentTimeMillis() + ".jpg";
            Path path = Paths.get(uploadDir + fileName);

            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "http://localhost:8080/photos/" + fileName;
            emp.setPhotoUrl(photoUrl);
            employeeRepo.save(emp);

            return photoUrl;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file: " + e.getMessage());
        }
    }

    @Override
    public void updateEmailPreference(Integer id, Boolean emailNotifications) {
        Employee emp = employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        emp.setEmailNotifications(emailNotifications);
        employeeRepo.save(emp);
    }

    @Override
    public void updatePassword(Integer empId, String currentPassword, String newPassword) {
        Employee emp = employeeRepo.findById(empId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        User user = emp.getUser();
        if (user == null) throw new ResourceNotFoundException("No associated user account found");

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("The current password you entered is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Override
    public void changePassword(Integer id, PasswordChangeDTO dto) {
        updatePassword(id, dto.getCurrentPassword(), dto.getNewPassword());
    }

    @Override
    public void updatePhotoUrl(Integer id, String photoUrl) {
        Employee emp = employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        emp.setPhotoUrl(photoUrl);
        employeeRepo.save(emp);
    }

    // --- QUERIES & STATS ---

    @Override
    public Employee getByUserId(Integer userId) {
        return employeeRepo.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No Employee profile linked to User ID: " + userId));
    }

    @Override
    public Employee getByEmail(String email) {
        return employeeRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
    }

    @Override
    public Map<String, Object> getDashboardStats(Integer id) {
        Employee emp = employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        LocalDate now = LocalDate.now();
        List<Object[]> summaryResult = attendanceRepo.summary(now.getYear(), now.getMonthValue());

        long present = 0, absent = 0, leave = 0;
        if (!summaryResult.isEmpty() && summaryResult.get(0) != null) {
            Object[] row = summaryResult.get(0);
            present = row[0] != null ? ((Number) row[0]).longValue() : 0;
            absent  = row[1] != null ? ((Number) row[1]).longValue() : 0;
            leave   = row[2] != null ? ((Number) row[2]).longValue() : 0;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("lastSalary", emp.getBasicSalary() != null ? emp.getBasicSalary() : 0);
        stats.put("remainingLeaves", 12);
        stats.put("attendanceSummary", new AttendanceSummaryDTO(present, absent, leave));
        return stats;
    }

    @Override
    public Map<Integer, Long> getActiveEmployeeStats() {
        List<Object[]> result = employeeRepo.countActiveEmployeesPerMonth();
        Map<Integer, Long> stats = new HashMap<>();
        for (Object[] row : result) {
            stats.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return stats;
    }

    // --- PRIVATE HELPER METHODS ---

    private void validateAndAttachForeignKeys(Employee employee) {
        if (employee.getDepartment() == null || employee.getDepartment().getDeptId() == null)
            throw new IllegalArgumentException("Department ID required");
        if (employee.getPosition() == null || employee.getPosition().getDesignationId() == null)
            throw new IllegalArgumentException("Designation ID required");

        Department dept = departmentRepo.findById(employee.getDepartment().getDeptId())
                .orElseThrow(() -> new ResourceNotFoundException("Dept not found"));
        Designation desig = designationRepo.findById(employee.getPosition().getDesignationId())
                .orElseThrow(() -> new ResourceNotFoundException("Desig not found"));

        employee.setDepartment(dept);
        employee.setPosition(desig);
    }

    private void saveBankAccount(Employee employee, BankAccount incomingBa) {
        if (incomingBa.getBank() != null && incomingBa.getBank().getBankId() != null && incomingBa.getAccountNumber() != null) {
            Bank bank = bankRepo.findById(incomingBa.getBank().getBankId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bank not found"));

            // Check if a primary account already exists for this employee to update it, else create new
            BankAccount accountToSave = bankAccountRepo.findByEmployeeEmpId(employee.getEmpId()).stream()
                    .filter(BankAccount::getIsPrimary)
                    .findFirst()
                    .orElse(new BankAccount());

            accountToSave.setEmployee(employee);
            accountToSave.setBank(bank);

            // Clean the string and parse to Long to match the Entity's data type
            try {
                String cleanAcc = String.valueOf(incomingBa.getAccountNumber()).replaceAll("[^0-9]", "");
                if (cleanAcc.isEmpty()) throw new IllegalArgumentException("Account number is invalid.");
                accountToSave.setAccountNumber(Long.parseLong(cleanAcc));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Account number must be numeric and within valid range.");
            }

            // Set mandatory fields from entity definition
            accountToSave.setAccountType(incomingBa.getAccountType() != null ? incomingBa.getAccountType() : "SALARY");
            accountToSave.setCurrency(incomingBa.getCurrency() != null ? incomingBa.getCurrency() : "NPR");
            accountToSave.setIsPrimary(true);

            bankAccountRepo.save(accountToSave);
        }
    }
}