package np.edu.nast.payroll.Payroll.service.impl;

import lombok.RequiredArgsConstructor;
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

    @Override
    public User create(User user) {
        // 1. Sanitize Username (No spaces)
        String sanitizedUsername = user.getUsername().replaceAll("\\s+", "");

        // 2. Resolve Username Conflicts
        userRepository.findByUsername(sanitizedUsername).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getIsActive())) {
                throw new RuntimeException("Username '" + sanitizedUsername + "' is already in use by an active staff.");
            } else {
                // If inactive, rename it to release the constraint
                String releaseTag = "_rel_" + System.currentTimeMillis();
                existing.setUsername(existing.getUsername() + releaseTag);
                existing.setEmail(existing.getEmail() + releaseTag);
                userRepository.saveAndFlush(existing);
            }
        });

        // 3. Resolve Email Conflicts
        userRepository.findByEmailIgnoreCase(user.getEmail()).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getIsActive())) {
                throw new EmailAlreadyExistsException("Email " + user.getEmail() + " is already in use by an active staff.");
            } else {
                // If inactive, rename it
                String releaseTag = "_rel_" + System.currentTimeMillis();
                existing.setEmail(existing.getEmail() + releaseTag);
                existing.setUsername(existing.getUsername() + releaseTag);
                userRepository.saveAndFlush(existing);
            }
        });

        // 4. Create New Account
        String tempPassword = generateRandomString(10);
        user.setUsername(sanitizedUsername);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setFirstLogin(true);
        user.setIsActive(true);

        User savedUser = userRepository.save(user);

        emailService.sendSimpleEmail(
                savedUser.getEmail(),
                "Account Created - NAST Payroll",
                "Your account has been created.\n\n" +
                        "Username: " + savedUser.getUsername() +
                        "\nTemporary Password: " + tempPassword +
                        "\n\nPlease login to setup your permanent account."
        );

        return savedUser;
    }

    @Override
    public User update(Integer id, User userDetails) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!existingUser.getEmail().equalsIgnoreCase(userDetails.getEmail())) {
            userRepository.findByEmailIgnoreCase(userDetails.getEmail()).ifPresent(other -> {
                if (Boolean.TRUE.equals(other.getIsActive())) {
                    throw new EmailAlreadyExistsException("Email already taken by an active account.");
                } else {
                    other.setEmail(other.getEmail() + "_rel_" + System.currentTimeMillis());
                    userRepository.saveAndFlush(other);
                }
            });
        }

        existingUser.setEmail(userDetails.getEmail());
        if (userDetails.getRole() != null) existingUser.setRole(userDetails.getRole());

        if (userDetails.getPassword() != null && !userDetails.getPassword().trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        return userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setIsActive(false);

        // RELEASE: Frees up username and email for future reuse
        String timestamp = "_del_" + System.currentTimeMillis();
        user.setEmail(user.getEmail() + timestamp);
        user.setUsername(user.getUsername() + timestamp);

        if (user.getEmployee() != null) {
            user.getEmployee().setIsActive(false);
            user.getEmployee().setEmail(user.getEmail());
        }

        userRepository.save(user);
    }

    @Override
    public void finalizeAccountSetup(String email, String newUsername, String newPassword, String token) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
            throw new IllegalArgumentException("Invalid verification code.");
        }

        String cleanUsername = newUsername.replaceAll("\\s+", "");
        user.setUsername(cleanUsername);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        user.setResetToken(null);
        user.setTokenExpiry(null);

        userRepository.save(user);
    }

    // --- Standard Methods ---
    @Override
    public List<User> getAll() { return userRepository.findByIsActiveTrue(); }

    @Override
    public User getById(Integer id) { return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }

    @Override
    public User getByEmail(String email) { return userRepository.findByEmailAndIsActiveTrue(email).orElse(null); }

    @Override
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmailAndIsActiveTrue(email.trim()).orElseThrow(() -> new ResourceNotFoundException("Active account not found"));
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setResetToken(otp);
        user.setTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token).orElseThrow(() -> new ResourceNotFoundException("Invalid token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);
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
        userRepository.findByIsActiveTrue().forEach(u -> {
            String otp = String.format("%06d", new SecureRandom().nextInt(999999));
            emailService.sendOtpEmail(u.getEmail(), otp);
        });
    }

    @Override
    public User setupDefaultAccount(Integer empId) { return new User(); }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}