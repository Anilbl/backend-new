package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.entity.Attendance;
import np.edu.nast.payroll.Payroll.repository.EmployeeRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeLeaveRepository;
import np.edu.nast.payroll.Payroll.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeLeaveRepository leaveRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    /**
     * UPDATED: Now filters totalWorkforce by isActive = true
     * and calculates attendance percentage based on active staff.
     */
    @GetMapping("/admin/stats")
    public Map<String, Object> getDashboardStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day) {

        Map<String, Object> stats = new HashMap<>();
        try {
            // 1. Determine the Target Date (Default to today)
            LocalDate now = LocalDate.now();
            LocalDate targetDate = (year != null && month != null && day != null)
                    ? LocalDate.of(year, month, day)
                    : now;

            // 2. Fetch data: Only count employees where isActive is true
            long totalActiveEmployees = employeeRepository.countByIsActiveTrue();
            long pendingLeaves = leaveRepository.countByStatus("PENDING");

            // Get attendance count for the specific selected date
            long presentOnDate = attendanceRepository.countByAttendanceDate(targetDate);

            // 3. Calculation based on the ACTIVE workforce
            String attendancePercentage = "0%";
            if (totalActiveEmployees > 0) {
                double percentage = ((double) presentOnDate / totalActiveEmployees) * 100;
                // Round and cap at 100% to avoid logic errors if data is messy
                attendancePercentage = Math.min(100, Math.round(percentage)) + "%";
            }

            stats.put("totalWorkforce", totalActiveEmployees);
            stats.put("leaveRequests", pendingLeaves);
            stats.put("dailyAttendance", attendancePercentage);

        } catch (Exception e) {
            e.printStackTrace();
            stats.put("totalWorkforce", 0);
            stats.put("leaveRequests", 0);
            stats.put("dailyAttendance", "0%");
        }
        return stats;
    }

    /**
     * UPDATED: Fetches attendance and ensures only records for active
     * employees are returned to the table.
     */
    @GetMapping("/recent-attendance")
    public List<Attendance> getRecentAttendance(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) String search) {
        try {
            LocalDate now = LocalDate.now();
            int filterYear = (year != null) ? year : now.getYear();
            int filterMonth = (month != null) ? month : now.getMonthValue();
            int filterDay = (day != null) ? day : now.getDayOfMonth();
            String filterSearch = (search != null) ? search : "";

            // Fetch filtered list from repository
            List<Attendance> list = attendanceRepository.findFilteredAttendance(
                    filterYear, filterMonth, filterDay, filterSearch
            );

            if (list == null) return new ArrayList<>();

            // Extra Filter: Ensure we only show attendance for currently active employees
            return list.stream()
                    .filter(attendance -> attendance.getEmployee() != null && attendance.getEmployee().getIsActive())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}