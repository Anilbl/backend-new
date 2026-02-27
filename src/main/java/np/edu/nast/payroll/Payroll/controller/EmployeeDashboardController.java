package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.repository.AttendanceRepository;
import np.edu.nast.payroll.Payroll.repository.LeaveBalanceRepository; // Need this
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class EmployeeDashboardController {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository; // Autowire the balance repo

    @GetMapping("/stats/{empId}")
    public Map<String, Object> getEmployeeDashboardStats(@PathVariable Integer empId) {
        Map<String, Object> stats = new HashMap<>();
        try {
            // 1. Calculate Monthly Attendance Percentage
            LocalDate now = LocalDate.now();
            int daysInMonth = now.lengthOfMonth();
            long daysPresent = attendanceRepository.countByEmployeeEmpIdAndAttendanceDateBetween(
                    empId,
                    now.withDayOfMonth(1),
                    now.withDayOfMonth(daysInMonth)
            );

            double percentage = (daysInMonth > 0) ? ((double) daysPresent / daysInMonth) * 100 : 0;
            stats.put("attendance", Math.round(percentage * 10.0) / 10.0 + "%");

            // 2. Fetch SUM of currentBalanceDays from LeaveBalance table
            // This matches the "Total Available Quota" logic in your LeaveManagement.js
            Integer totalAvailableQuota = leaveBalanceRepository.sumCurrentBalanceByEmployeeId(empId);

            stats.put("remainingLeaves", totalAvailableQuota != null ? totalAvailableQuota : 0);

            // 3. Placeholder for Net Salary
            stats.put("netSalary", "Rs. 0");

        } catch (Exception e) {
            e.printStackTrace();
            stats.put("attendance", "0.0%");
            stats.put("remainingLeaves", 0);
            stats.put("netSalary", "Rs. 0");
        }
        return stats;
    }
}