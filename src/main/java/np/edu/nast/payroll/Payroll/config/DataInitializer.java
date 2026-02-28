package np.edu.nast.payroll.Payroll.config;

import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.entity.*;
import np.edu.nast.payroll.Payroll.repository.*;
import np.edu.nast.payroll.Payroll.service.EmailService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Ensure Roles exist
        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("ADMIN").build()));

        roleRepository.findByRoleName("EMPLOYEE")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("EMPLOYEE").build()));

        // 2. Safeguard: Only run if database has no users
        if (userRepository.count() == 0) {

            // --- CONFIGURATION ---
            String clientEmail = "blanil928@gmail.com";
            String username = "superadmin";
            String rawPassword = UUID.randomUUID().toString().substring(0, 8);

            // 3. Create mandatory Department and Designation
            Department defaultDept = departmentRepository.findByDeptName("Administration")
                    .orElseGet(() -> departmentRepository.save(Department.builder()
                            .deptName("Administration")
                            .build()));

            Designation defaultDesignation = designationRepository.findByDesignationTitle("System Administrator")
                    .orElseGet(() -> designationRepository.save(Designation.builder()
                            .designationTitle("System Administrator")
                            .baseSalary(0.0)
                            .build()));

            // 4. Create the Superadmin User
            User adminUser = User.builder()
                    .username(username)
                    .email(clientEmail)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(adminRole)
                    .isFirstLogin(true)
                    .isActive(true) // Ensure the user is active
                    .createdAt(LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(adminUser);

            // 5. Create the linked Employee with ALL mandatory fields
            // The error "Column 'is_ssf_enrolled' cannot be null" is fixed here
            Employee adminEmployee = Employee.builder()
                    .user(savedUser)
                    .firstName("System")
                    .lastName("Administrator")
                    .gender("OTHER")              // Added: Mandatory column
                    .email(clientEmail)
                    .contact("9800000000")
                    .address("Central Office")
                    .education("Master")
                    .joiningDate(LocalDate.now())
                    .basicSalary(0.0)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .employmentStatus("PERMANENT")
                    .maritalStatus("N/A")
                    .department(defaultDept)
                    .position(defaultDesignation)
                    .photoUrl("default-profile.png") // Added: Mandatory column
                    .isSsfEnrolled(false)           // Added: Fixes your specific SQL error
                    .emailNotifications(true)
                    .build();

            employeeRepository.save(adminEmployee);

            // 6. Send Credentials
            try {
                String emailContent = "Welcome to the NAST Payroll System.\n\n" +
                        "Your Superadmin account has been created successfully.\n\n" +
                        "Username: " + username + "\n" +
                        "Temporary Password: " + rawPassword + "\n\n" +
                        "Please log in at your earliest convenience and change your password for security purposes.";

                emailService.sendSimpleEmail(clientEmail, "Initial Admin Credentials - Payroll System", emailContent);

                System.out.println("--------------------------------------------------");
                System.out.println(">> SYSTEM INITIALIZED SUCCESSFULLY");
                System.out.println(">> Admin Username: " + username);
                System.out.println(">> Admin Email: " + clientEmail);
                System.out.println(">> Temporary Password: " + rawPassword);
                System.out.println("--------------------------------------------------");
            } catch (Exception e) {
                System.err.println(">> DATA INITIALIZED BUT EMAIL FAILED: " + e.getMessage());
            }
        }
    }
}