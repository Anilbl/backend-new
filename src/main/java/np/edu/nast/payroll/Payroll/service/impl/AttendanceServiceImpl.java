package np.edu.nast.payroll.Payroll.service.impl;

import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.entity.Attendance;
import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.EmployeeLeave;
import np.edu.nast.payroll.Payroll.entity.Holiday;
import np.edu.nast.payroll.Payroll.exception.ResourceNotFoundException;
import np.edu.nast.payroll.Payroll.repository.AttendanceRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeLeaveRepository;
import np.edu.nast.payroll.Payroll.repository.HolidayRepository;
import np.edu.nast.payroll.Payroll.service.AttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final HolidayRepository holidayRepository; // NEW INJECTION

    @Override
    @Transactional
    public Attendance createAttendance(Attendance attendance) {
        if (attendance.getEmployee() == null || attendance.getEmployee().getEmpId() == null) {
            throw new IllegalArgumentException("Employee identity is missing. Please re-login.");
        }

        Integer empId = attendance.getEmployee().getEmpId();
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee record not found for ID: " + empId));

        LocalDate today = LocalDate.now();

        // Check if today is a Holiday
        boolean isHoliday = holidayRepository.findByHolidayDateBetween(today, today).size() > 0;
        if (isHoliday) {
            // Optional: You can allow check-in on holidays but maybe mark as overtime?
            // For now, we allow it but keep the logic simple.
        }

        boolean isOnLeave = employeeLeaveRepository.existsByEmployee_EmpIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                empId, "Approved", today, today
        );

        if (isOnLeave) {
            throw new IllegalArgumentException("You are currently on leave. You cannot check in during your leave period.");
        }

        Optional<Attendance> lastRecord = attendanceRepository.findTopByEmployee_EmpIdOrderByAttendanceIdDesc(empId);
        if (lastRecord.isPresent()) {
            LocalDateTime lastCheckIn = lastRecord.get().getCheckInTime();
            long hoursSinceLastIn = Duration.between(lastCheckIn, LocalDateTime.now()).toHours();
            if (hoursSinceLastIn < 10) {
                throw new IllegalArgumentException("10-Hour Rule: You can only check in once every 10 hours.");
            }
        }

        attendance.setEmployee(employee);
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setAttendanceDate(LocalDate.now());
        attendance.setStatus("PRESENT");

        return attendanceRepository.save(attendance);
    }

    @Override
    @Transactional
    public Attendance updateAttendance(Integer id, Attendance updated) {
        Attendance existing = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record " + id + " not found"));

        if (updated.getCheckOutTime() != null || "Checked Out".equals(updated.getStatus())) {
            existing.setCheckOutTime(LocalDateTime.now());
            existing.setStatus("COMPLETED");
        }

        return attendanceRepository.save(existing);
    }

    @Override
    @Transactional
    public List<Attendance> getAttendanceByEmployee(Integer empId) {
        if (empId == null) return List.of();
        List<Attendance> records = attendanceRepository.findByEmployee_EmpId(empId);

        return records.stream()
                .sorted((a, b) -> b.getAttendanceDate().compareTo(a.getAttendanceDate()))
                .map(record -> {
                    if (record.getCheckOutTime() == null) {
                        long hoursActive = Duration.between(record.getCheckInTime(), LocalDateTime.now()).toHours();
                        if (hoursActive >= 8) {
                            record.setCheckOutTime(record.getCheckInTime().plusHours(8));
                            record.setStatus("AUTO-CHECKOUT");
                            attendanceRepository.save(record);
                        }
                    }
                    return record;
                }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getMonthlyStats(Integer empId, int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        LocalDate today = LocalDate.now();

        // Boundary for calculation (current date if looking at current month, else end of month)
        LocalDate boundaryDate = (today.isBefore(endOfMonth) && today.getYear() == year && today.getMonthValue() == month)
                ? today : endOfMonth;

        // 1. Fetch Basic Totals
        long present = attendanceRepository.countByEmployee_EmpIdAndStatusAndAttendanceDateBetween(empId, "PRESENT", startOfMonth, endOfMonth)
                + attendanceRepository.countByEmployee_EmpIdAndStatusAndAttendanceDateBetween(empId, "COMPLETED", startOfMonth, endOfMonth)
                + attendanceRepository.countByEmployee_EmpIdAndStatusAndAttendanceDateBetween(empId, "AUTO-CHECKOUT", startOfMonth, endOfMonth);

        // 2. Calculate Approved Leaves
        List<EmployeeLeave> approvedLeaves = employeeLeaveRepository.findRelevantLeaves(empId, "Approved", startOfMonth, endOfMonth);
        long totalLeaveDays = 0;
        for (EmployeeLeave leave : approvedLeaves) {
            LocalDate overlapStart = leave.getStartDate().isBefore(startOfMonth) ? startOfMonth : leave.getStartDate();
            LocalDate overlapEnd = leave.getEndDate().isAfter(endOfMonth) ? endOfMonth : leave.getEndDate();
            totalLeaveDays += ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
        }

        // 3. HOLIDAY & SATURDAY LOGIC (The "Off-Days")
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(startOfMonth, endOfMonth);
        Set<LocalDate> offDaysSet = new HashSet<>();

        // Add all Saturdays to the set
        for (LocalDate date = startOfMonth; !date.isAfter(boundaryDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                offDaysSet.add(date);
            }
        }

        // Add Holidays to the set (Set automatically handles duplicates if Holiday is on a Saturday)
        for (Holiday h : holidays) {
            if (!h.getHolidayDate().isAfter(boundaryDate)) {
                offDaysSet.add(h.getHolidayDate());
            }
        }

        long totalOffDays = offDaysSet.size();
        long totalDaysElapsed = ChronoUnit.DAYS.between(startOfMonth, boundaryDate) + 1;

        // 4. Calculate Absence
        // Absent = Total Days - (Present + Leaves + OffDays)
        // We use Math.max to prevent negative numbers if someone works on a holiday
        long absent = Math.max(0, totalDaysElapsed - present - totalLeaveDays - totalOffDays);

        Map<String, Object> stats = new HashMap<>();
        stats.put("present", present);
        stats.put("leaveTaken", totalLeaveDays);
        stats.put("holidays", totalOffDays); // Total Saturdays + Public Holidays
        stats.put("absent", absent);
        stats.put("totalDaysInMonth", startOfMonth.lengthOfMonth());

        return stats;
    }

    @Override public void deleteAttendance(Integer id) { attendanceRepository.deleteById(id); }
    @Override public Attendance getAttendanceById(Integer id) { return attendanceRepository.findById(id).orElse(null); }
    @Override public List<Attendance> getAllAttendance() { return attendanceRepository.findAll(); }
}