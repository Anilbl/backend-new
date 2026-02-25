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
import org.springframework.security.crypto.password.PasswordEncoder; // Add this
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder; // Add this

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           EmployeeRepository employeeRepository,
                           JwtUtils jwtUtils,
                           PasswordEncoder passwordEncoder) { // Add this to constructor
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

        // 1. Fetch user
        User user = userRepository.findByUsername(inputUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + inputUsername));

        // --- START DEBUG BLOCK ---
        System.out.println("DEBUG: Attempting login for user: " + inputUsername);
        System.out.println("DEBUG: Raw Password from UI: " + request.getPassword());
        System.out.println("DEBUG: Encoded Password from DB: " + user.getPassword());

        boolean manualMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        System.out.println("DEBUG: Does BCrypt match? " + manualMatch);
        // --- END DEBUG BLOCK ---

        try {
            // 2. Attempt Authentication
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

        // 3. Fetch linked Employee profile
        Employee employee = employeeRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new RuntimeException("No Employee profile linked to: " + user.getEmail()));

        // 4. Role Formatting
        String roleName = user.getRole().getRoleName().toUpperCase();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        // 5. Generate Token
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