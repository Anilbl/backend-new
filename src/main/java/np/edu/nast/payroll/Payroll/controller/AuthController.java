package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.dto.LoginRequestDTO;
import np.edu.nast.payroll.Payroll.dto.LoginResponseDTO;
import np.edu.nast.payroll.Payroll.dto.SetupRequestDTO;
import np.edu.nast.payroll.Payroll.service.AuthService;
import np.edu.nast.payroll.Payroll.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;

    @Autowired
    private UserService userService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Standard Login: Uses the username and password provided in the request body.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        // DEBUG LOGS: Check your IntelliJ console to see these!
        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username Received: " + request.getUsername());
        // Do not log actual passwords in production, but for debugging:
        System.out.println("Password Length: " + (request.getPassword() != null ? request.getPassword().length() : "NULL"));

        LoginResponseDTO response = authService.authenticateUser(request);

        System.out.println("Login Success for: " + request.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Sends OTP to the email provided in the request map.
     */
    @PostMapping("/request-setup-token")
    public ResponseEntity<String> requestSetupToken(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            userService.initiatePasswordReset(email);
            return ResponseEntity.ok("Verification code sent to your email.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Updates the user with the NEW username and password from the frontend.
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
            return ResponseEntity.ok("Account setup complete! You can now log in.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}