package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.dto.SetupRequestDTO;
import np.edu.nast.payroll.Payroll.entity.User;
import np.edu.nast.payroll.Payroll.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    private UserService userService;

    // --- NEW ONBOARDING & SETUP ENDPOINTS ---

    /**
     * Used by frontend to verify user details via email before/during setup
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUserByEmail(@RequestParam String email) {
        try {
            User user = userService.getByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with email: " + email);
            }
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Triggered from InitialSetup.jsx when user clicks "Send Verification Code"
     */
    @PostMapping("/request-setup-token")
    public ResponseEntity<String> requestSetupToken(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            userService.initiatePasswordReset(email); // Reuses the 6-digit OTP logic
            return ResponseEntity.ok("Verification code sent to your email.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Finalizes the account setup using the specialized SetupRequestDTO.
     * Updates username, password, and sets isFirstLogin to false.
     */
    @PostMapping("/finalize-setup")
    public ResponseEntity<String> finalizeSetup(@RequestBody SetupRequestDTO setupDto) {
        try {
            userService.finalizeAccountSetup(
                    setupDto.getEmail(),
                    setupDto.getNewUsername(),
                    setupDto.getNewPassword(),
                    setupDto.getToken()
            );
            return ResponseEntity.ok("Account setup complete! You can now log in with your new credentials.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // --- EXISTING USER MANAGEMENT ---

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Integer id) {
        try {
            User user = userService.getById(id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Integer id, @RequestBody User user) {
        try {
            return ResponseEntity.ok(userService.update(id, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public List<User> getUsers() {
        // Make sure this calls service.getAll() which filters isActive = true
        return userService.getAll();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer id) {
        try {
            userService.delete(id);
            return ResponseEntity.ok("User and associated profile deactivated successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            User created = userService.create(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // --- FORGOT & RESET PASSWORD (EXISTING) ---

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam("email") String email) {
        try {
            userService.initiatePasswordReset(email);
            return ResponseEntity.ok("OTP sent to your email.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing request.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        try {
            userService.resetPassword(token, newPassword);
            return ResponseEntity.ok("Success: Password updated.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}