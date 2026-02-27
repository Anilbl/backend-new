package np.edu.nast.payroll.Payroll.service.impl;

import np.edu.nast.payroll.Payroll.dto.LoginRequestDTO;
import np.edu.nast.payroll.Payroll.dto.LoginResponseDTO;
import np.edu.nast.payroll.Payroll.entity.User;
import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.repository.UserRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeRepository;
import np.edu.nast.payroll.Payroll.security.JwtUtils;
import np.edu.nast.payroll.Payroll.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           EmployeeRepository employeeRepository,
                           JwtUtils jwtUtils,
                           PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponseDTO authenticateUser(LoginRequestDTO request) {
        String inputUsername = request.getUsername();

        // 1. Fetch user (Check if they exist at all)
        User user = userRepository.findByUsername(inputUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + inputUsername));

        // 2. CHECK IF USER IS ACTIVE
        // If the account is soft-deleted/inactive, block login immediately.
        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new RuntimeException("Account is inactive. Please contact the administrator.");
        }

        try {
            // 3. Attempt Authentication (Password check)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(inputUsername, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            if (user.isFirstLogin()) {
                throw new RuntimeException("Initial setup required. Please use temporary password.");
            }
            throw new RuntimeException("Incorrect password for user: " + inputUsername);
        } catch (AuthenticationException e) {
            throw new RuntimeException("Authentication failed for '" + inputUsername + "'.");
        }

        // 4. Fetch linked Employee profile
        Employee employee = employeeRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new RuntimeException("No Employee profile linked to: " + user.getEmail()));

        // 5. Role Formatting
        String roleName = user.getRole().getRoleName().toUpperCase();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        // 6. Generate Token
        String token = jwtUtils.generateToken(user.getUsername(), roleName);

        return new LoginResponseDTO(
                user.getUserId(),
                employee.getEmpId(),
                user.getUsername(),
                user.getEmail(),
                roleName,
                token,
                user.isFirstLogin(),
                user.isAdmin(),
                user.isAccountant(),
                user.isHasEmployeeRole()
        );
    }
}