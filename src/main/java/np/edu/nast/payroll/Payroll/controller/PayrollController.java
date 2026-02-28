package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.dto.CommandCenterDTO;
import np.edu.nast.payroll.Payroll.dto.EmployeePayrollHistoryDTO;
import np.edu.nast.payroll.Payroll.entity.Payroll;
import np.edu.nast.payroll.Payroll.reportdto.PayrollSummaryDTO;
import np.edu.nast.payroll.Payroll.service.PayrollService;
import np.edu.nast.payroll.Payroll.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payrolls")
@CrossOrigin(origins = "http://localhost:5173")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private EmailService emailService;

    /**
     * COMMAND CENTER ENDPOINT
     * Fetches departmental summaries and employee rows for the management dashboard.
     */
    @GetMapping("/command-center")
    public ResponseEntity<?> getCommandCenter(
            @RequestParam int month,
            @RequestParam int year) {
        try {
            CommandCenterDTO dashboardData = payrollService.getCommandCenterData(month, year);
            return ResponseEntity.ok(dashboardData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error loading command center: " + e.getMessage()));
        }
    }

    /**
     * STEP 1: PREVIEW
     * Calculates payroll based on inputs (basic, rent, dearness, extras)
     * but DOES NOT save to database. Returns a transient Payroll object.
     */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody Map<String, Object> payload) {
        try {
            Payroll previewData = payrollService.calculatePreview(payload);
            return ResponseEntity.ok(previewData);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred."));
        }
    }

    /**
     * STEP 2: PROCESS
     * Saves the calculated payroll to the database with "PENDING_PAYMENT" status.
     */
    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestBody Map<String, Object> payload) {
        try {
            Payroll processedPayroll = payrollService.processPayroll(payload);
            return ResponseEntity.ok(processedPayroll);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * FINAL DISBURSEMENT
     * Updates status to "PAID" and records the bank transaction reference.
     */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<?> finalizePayroll(
            @PathVariable Integer id,
            @RequestBody Map<String, String> payload) {
        try {
            String transactionRef = payload.getOrDefault("transactionRef", "N/A");
            payrollService.finalizePayroll(id, transactionRef);
            return ResponseEntity.ok(Map.of("message", "Payroll finalized and totals updated."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * EMPLOYEE HISTORY (Audit Modal)
     * Fetches all previous payroll records for a specific employee.
     */
    @GetMapping("/employee/{empId}/history")
    public ResponseEntity<List<EmployeePayrollHistoryDTO>> getHistory(@PathVariable Integer empId) {
        // Renamed to match the updated Interface/Service implementation
        return ResponseEntity.ok(payrollService.getPayrollByEmployeeId(empId));
    }

    /**
     * VOID PAYROLL
     * Marks a payroll record as VOIDED if processing was incorrect.
     */
    @PutMapping("/{id}/void")
    public ResponseEntity<Payroll> voidPayroll(@PathVariable Integer id) {
        Payroll voided = payrollService.voidPayroll(id);
        return ResponseEntity.ok(voided);
    }

    /**
     * SEND EMAIL ENDPOINT
     * Generates a PDF and emails it to the employee.
     */
    @PostMapping("/{id}/send-email")
    public ResponseEntity<?> sendEmail(@PathVariable Integer id) {
        try {
            Payroll payroll = payrollService.getPayrollById(id);
            if (payroll == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Payroll record not found."));
            }
            emailService.generateAndSendPayslip(payroll, "MANUAL_UI_TRIGGER");
            return ResponseEntity.ok().body(Map.of("message", "Payslip PDF email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Email Error: " + e.getMessage()));
        }
    }

    /**
     * SUMMARY METRICS
     * Provides the high-level stats (Total Net, Total Tax, etc.) for the header cards.
     */
    @GetMapping("/salary-summary")
    public ResponseEntity<PayrollSummaryDTO> getSalarySummary(
            @RequestParam int month,
            @RequestParam int year) {
        try {
            PayrollSummaryDTO summary = payrollService.getSalarySummary(month, year);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public List<Payroll> getAll() {
        return payrollService.getAllPayrolls();
    }
}