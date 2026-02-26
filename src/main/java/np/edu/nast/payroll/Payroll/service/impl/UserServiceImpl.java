package np.edu.nast.payroll.Payroll.service.impl;

import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.User;
import np.edu.nast.payroll.Payroll.exception.EmailAlreadyExistsException;
import np.edu.nast.payroll.Payroll.exception.ResourceNotFoundException;
import np.edu.nast.payroll.Payroll.repository.UserRepository;
import np.edu.nast.payroll.Payroll.service.EmailService;
import np.edu.nast.payroll.Payroll.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // =========================================================
    // CREATE USER (Admin Side)
    // =========================================================
    @Override
    public User create(User user) {
        if (userRepository.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(
                    "A user with the " + user.getEmail() + " email already exists."
            );
        }

        String tempPassword = generateRandomString(10);

        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setFirstLogin(true);
        user.setIsActive(true); // Ensure new user is active
        user.setStatus("ACTIVE");

        User savedUser = userRepository.save(user);

        emailService.sendSimpleEmail(
                savedUser.getEmail(),
                "Account Created - NAST Payroll",
                "Your account has been created.\n\n" +
                        "Default Username: " + savedUser.getUsername() +
                        "\nDefault Password: " + tempPassword +
                        "\n\nPlease login to setup your permanent account."
        );

        return savedUser;
    }

    // =========================================================
    // LIST USERS (Soft Delete Filtered)
    // =========================================================
    @Override
    public List<User> getAll() {
        // Matches Interface name: getAll()
        // Filters by: is_active = true
        return userRepository.findByIsActiveTrue();
    }

    // =========================================================
    // DELETE USER (Soft Delete Implementation)
    // =========================================================
    @Override
    @Transactional
    public void delete(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Soft delete the User
        user.setIsActive(false);
        user.setStatus("DELETED");

        // 2. Explicitly find and soft delete the Employee
        // This ensures the database actually receives the update for the employee table
        if (user.getEmployee() != null) {
            Employee emp = user.getEmployee();
            emp.setIsActive(false);
            // Saving the user with CascadeType.ALL should work,
            // but explicit save is a guaranteed fix for 'no changes' issues.
        }

        userRepository.save(user);
    }

    // =========================================================
    // FINALIZE ACCOUNT SETUP (First Login)
    // =========================================================
    @Override
    public void finalizeAccountSetup(String email, String newUsername, String newPassword, String token) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
            throw new IllegalArgumentException("Invalid verification code.");
        }

        if (user.getTokenExpiry() != null && user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired.");
        }

        user.setUsername(newUsername.trim());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        user.setResetToken(null);
        user.setTokenExpiry(null);

        userRepository.save(user);
    }

    // =========================================================
    // PASSWORD RESET LOGIC
    // =========================================================
    @Override
    public void initiatePasswordReset(String email) {
        // Find only active users for password reset
        User user = userRepository.findByEmailAndIsActiveTrue(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Active account with this email not found"));

        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setResetToken(otp);
        user.setTokenExpiry(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired token"));

        if (user.getTokenExpiry() != null && user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setTokenExpiry(null);

        userRepository.save(user);
    }

    // =========================================================
    // UPDATE USER
    // =========================================================
    @Override
    public User update(Integer id, User userDetails) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        existingUser.setUsername(userDetails.getUsername());
        existingUser.setEmail(userDetails.getEmail());
        existingUser.setStatus(userDetails.getStatus());

        if (userDetails.getRole() != null) {
            existingUser.setRole(userDetails.getRole());
        }

        if (userDetails.getPassword() != null && !userDetails.getPassword().trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        return userRepository.save(existingUser);
    }

    @Override
    public User getById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    public User getByEmail(String email) {
        return userRepository.findByEmailAndIsActiveTrue(email).orElse(null);
    }

    @Override
    public boolean canSwitchRole(User user, String roleName) {
        if (user == null || roleName == null) return false;
        return switch (roleName.toUpperCase()) {
            case "ADMIN" -> user.isAdmin();
            case "ACCOUNTANT" -> user.isAccountant();
            case "EMPLOYEE" -> user.isHasEmployeeRole();
            default -> false;
        };
    }

    @Override
    public void sendOtpToAllUsers() {
        List<User> users = userRepository.findByIsActiveTrue();
        for (User user : users) {
            String otp = String.format("%06d", new SecureRandom().nextInt(999999));
            emailService.sendOtpEmail(user.getEmail(), otp);
        }
    }

    @Override
    public User setupDefaultAccount(Integer empId) {
        // To be implemented: Logic to link Employee to User setup
        return new User();
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}