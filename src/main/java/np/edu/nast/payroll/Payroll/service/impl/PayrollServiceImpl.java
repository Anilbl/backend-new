package np.edu.nast.payroll.Payroll.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.edu.nast.payroll.Payroll.dto.*;
import np.edu.nast.payroll.Payroll.entity.*;
import np.edu.nast.payroll.Payroll.reportdto.DepartmentSummaryDTO;
import np.edu.nast.payroll.Payroll.reportdto.PayrollSummaryDTO;
import np.edu.nast.payroll.Payroll.repository.*;
import np.edu.nast.payroll.Payroll.service.PayrollService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private final PayrollRepository payrollRepo;
    private final EmployeeRepository employeeRepo;
    private final SalaryComponentRepository salaryComponentRepo;
    private final TaxSlabRepository taxSlabRepo;
    private final MonthlyInfoRepository monthlyInfoRepo;
    private final UserRepository userRepo;
    private final PayGroupRepository payGroupRepo;
    private final PaymentMethodRepository paymentMethodRepo;
    private final AttendanceRepository attendanceRepo;
    private final EmployeeLeaveRepository employeeLeaveRepo;
    private final HolidayRepository holidayRepo;

    @Override
    public PayrollSummaryDTO getSalarySummary(int month, int year) {
        Object result = payrollRepo.getOverallMetrics(month, year);
        List<DepartmentSummaryDTO> depts = payrollRepo.getDepartmentalSummary(month, year);
        PayrollSummaryDTO summary = new PayrollSummaryDTO();

        if (result != null) {
            Object[] row = (Object[]) result;
            summary.setTotalGross(row[0] != null ? ((Number) row[0]).doubleValue() : 0.0);
            summary.setTotalDeductions(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
            summary.setTotalNet(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            summary.setTotalTax(row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
            summary.setTotalSSF(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0);
            summary.setTotalOvertime(row[5] != null ? ((Number) row[5]).doubleValue() : 0.0);
            summary.setPaidCount(row[6] != null ? ((Number) row[6]).longValue() : 0L);
        } else {
            summary.setTotalGross(0.0); summary.setTotalDeductions(0.0); summary.setTotalNet(0.0);
            summary.setTotalTax(0.0); summary.setTotalSSF(0.0); summary.setTotalOvertime(0.0);
            summary.setPaidCount(0L);
        }
        summary.setDepartments(depts != null ? depts : new ArrayList<>());
        // Summary count should also technically only reflect active employees
        summary.setTotalEmployees(employeeRepo.findAll().stream().filter(e -> Boolean.TRUE.equals(e.getIsActive())).count());
        return summary;
    }

    @Override
    public List<PayrollDashboardDTO> getBatchCalculation(String month, int year) {
        // Filter: Only include active employees for batch calculation
        List<Employee> activeEmployees = employeeRepo.findAll().stream()
                .filter(emp -> Boolean.TRUE.equals(emp.getIsActive()))
                .toList();

        int monthValue = parseMonthValue(month);
        if (monthValue == -1) return new ArrayList<>();

        LocalDate periodStart = LocalDate.of(year, monthValue, 1);
        LocalDate periodEnd = periodStart.plusMonths(1);
        int totalDaysInMonth = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
        double holidayCount = countPublicHolidaysInPeriod(periodStart, periodEnd);

        return activeEmployees.stream().map(emp -> {
            double physicalDays = countAttendanceDaysInternal(emp.getEmpId(), periodStart, periodEnd);
            double paidLeaveDays = calculatePaidLeaveDaysInternal(emp.getEmpId(), periodStart, periodEnd);
            double saturdays = countSaturdaysInPeriod(periodStart, periodEnd);
            double totalPaidDays = Math.min(totalDaysInMonth, physicalDays + paidLeaveDays + saturdays + holidayCount);

            double baseSalary = (emp.getBasicSalary() != null && emp.getBasicSalary() > 0)
                    ? emp.getBasicSalary() : getFallbackBasicFromComponents();

            double perDayRate = baseSalary / totalDaysInMonth;
            double actualEarned = (totalPaidDays >= totalDaysInMonth) ? baseSalary : (totalPaidDays * perDayRate);

            return PayrollDashboardDTO.builder()
                    .empId(emp.getEmpId())
                    .fullName(emp.getFirstName() + " " + emp.getLastName())
                    .basicSalary(baseSalary)
                    .earnedSalary(round(actualEarned))
                    .totalWorkedHours(totalPaidDays)
                    .maritalStatus(emp.getMaritalStatus())
                    .build();
        }).toList();
    }

    public CommandCenterDTO getCommandCenterData(int month, int year) {
        LocalDate periodStart = LocalDate.of(year, month, 1);

        // Filter: Fetch all but stream and filter only active ones for display/processing
        List<Employee> activeEmployees = employeeRepo.findAll().stream()
                .filter(emp -> Boolean.TRUE.equals(emp.getIsActive()))
                .toList();

        List<Payroll> dbPayrolls = payrollRepo.findByPayPeriodStart(periodStart);

        Map<Integer, Payroll> payrollMap = dbPayrolls.stream()
                .filter(p -> !"VOIDED".equals(p.getStatus()))
                .collect(Collectors.toMap(p -> p.getEmployee().getEmpId(), p -> p, (p1, p2) -> p1));

        List<PayrollDashboardDTO> previews = getBatchCalculation(String.valueOf(month), year);
        Map<Integer, PayrollDashboardDTO> previewMap = previews.stream()
                .collect(Collectors.toMap(PayrollDashboardDTO::getEmpId, p -> p));

        List<EmployeePayrollRowDTO> rows = activeEmployees.stream().map(emp -> {
            Payroll existing = payrollMap.get(emp.getEmpId());
            PayrollDashboardDTO preview = previewMap.get(emp.getEmpId());

            if (existing != null) {
                return EmployeePayrollRowDTO.builder()
                        .empId(emp.getEmpId())
                        .fullName(emp.getFirstName() + " " + emp.getLastName())
                        .basicSalary(emp.getBasicSalary())
                        .earnedSalary(existing.getBasicSalary())
                        .payrollId(existing.getPayrollId())
                        .festivalBonus(existing.getFestivalBonus())
                        .bonuses(existing.getOtherBonuses())
                        .citContribution(existing.getCitContribution())
                        .status(existing.getStatus())
                        .build();
            } else {
                double earned = preview != null ? preview.getEarnedSalary() : (emp.getBasicSalary() != null ? emp.getBasicSalary() : 0.0);
                return EmployeePayrollRowDTO.builder()
                        .empId(emp.getEmpId())
                        .fullName(emp.getFirstName() + " " + emp.getLastName())
                        .basicSalary(emp.getBasicSalary())
                        .earnedSalary(earned)
                        .payrollId(null)
                        .festivalBonus(0.0)
                        .bonuses(0.0)
                        .citContribution(0.0)
                        .status(earned > 0 ? "READY" : "NO_EARNINGS")
                        .build();
            }
        }).collect(Collectors.toList());

        CommandCenterDTO dto = new CommandCenterDTO();
        dto.setEmployeeRows(rows);
        dto.setMonthlyPayrollTotal(dbPayrolls.stream().filter(p -> "PAID".equals(p.getStatus())).mapToDouble(Payroll::getNetSalary).sum());
        dto.setPendingVerifications((int) rows.stream().filter(r -> "READY".equals(r.getStatus())).count());
        dto.setPayrollStatus(rows.stream().anyMatch(r -> "PAID".equals(r.getStatus())) ? "Processing" : "Idle");
        dto.setCompliancePercentage(100);
        return dto;
    }

    @Override
    public Payroll calculatePreview(Map<String, Object> payload) {
        log.info("--- [PAYROLL ENGINE] PROCESSING PREVIEW ---");

        Integer empId = resolveEmpId(payload);
        Employee employee = employeeRepo.findById(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Safety check if someone tries to calculate a preview for an inactive employee via API
        if (Boolean.FALSE.equals(employee.getIsActive())) {
            throw new RuntimeException("Cannot process payroll for an inactive employee.");
        }

        int year = (payload.get("year") != null) ? Integer.parseInt(payload.get("year").toString()) : LocalDate.now().getYear();
        int monthValue = (payload.get("month") != null) ? parseMonthValue(payload.get("month").toString()) : LocalDate.now().getMonthValue();

        LocalDate periodStart = LocalDate.of(year, monthValue, 1);
        LocalDate periodEnd = periodStart.plusMonths(1);
        int totalDaysInMonth = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);

        validatePayrollPeriod(empId, periodStart);

        double baseSalaryConfig = (employee.getBasicSalary() != null && employee.getBasicSalary() > 0)
                ? employee.getBasicSalary() : getFallbackBasicFromComponents();

        // 1. DETERMINE EARNED SALARY
        double finalEarnedSalary;
        if (payload.get("earnedSalary") != null && Double.parseDouble(payload.get("earnedSalary").toString()) > 0) {
            finalEarnedSalary = Double.parseDouble(payload.get("earnedSalary").toString());
        } else {
            double physicalDays = countAttendanceDaysInternal(empId, periodStart, periodEnd);
            double paidLeaveDays = calculatePaidLeaveDaysInternal(empId, periodStart, periodEnd);
            double saturdays = countSaturdaysInPeriod(periodStart, periodEnd);
            double holidayCount = countPublicHolidaysInPeriod(periodStart, periodEnd);
            double totalPaidDays = Math.min(totalDaysInMonth, physicalDays + paidLeaveDays + saturdays + holidayCount);

            double perDayRate = baseSalaryConfig / totalDaysInMonth;
            finalEarnedSalary = (totalPaidDays >= totalDaysInMonth) ? baseSalaryConfig : round(totalPaidDays * perDayRate);
        }

        // 2. SSF LOGIC
        List<SalaryComponent> dbComponents = salaryComponentRepo.findAll();
        boolean isEnrolled = (employee.getIsSsfEnrolled() != null && employee.getIsSsfEnrolled());

        double ssfContribution = 0.0;
        if (isEnrolled) {
            double ssfPercentage = getComponentDefault(dbComponents, "ssf", 11.0);
            ssfContribution = round(baseSalaryConfig * (ssfPercentage / 100.0));
        }

        double dearnessAmt = getComponentDefault(dbComponents, "Dearness Allowance", 7380.0);
        double hraPercentage = getComponentDefault(dbComponents, "House Rent Allowance", 0.0);
        double calculatedHra = round(baseSalaryConfig * (hraPercentage / 100.0));

        // 3. CAPTURE OTHER MANUAL INPUTS
        double festivalBonus = parseDouble(payload, "festivalBonus");
        double otherBonuses = payload.containsKey("bonuses") ? parseDouble(payload, "bonuses") : parseDouble(payload, "otherBonuses");
        double citContribution = parseDouble(payload, "citContribution");

        Payroll payroll = Payroll.builder()
                .employee(employee)
                .payGroup(resolvePayGroup(employee))
                .basicSalary(round(finalEarnedSalary))
                .ssfContribution(ssfContribution)
                .payPeriodStart(periodStart)
                .payPeriodEnd(periodEnd.minusDays(1))
                .status("PREVIEW")
                .extraComponents(new ArrayList<>())
                .build();

        // 4. ADD BREAKDOWN COMPONENTS
        payroll.addExtraComponent("Basic Salary (Earned)", round(finalEarnedSalary), "EARNING", "FIXED", "Manual/Attendance");
        payroll.addExtraComponent("Dearness Allowance", dearnessAmt, "EARNING", "FIXED", "Config");
        if (calculatedHra > 0) payroll.addExtraComponent("House Rent Allowance", calculatedHra, "EARNING", "PERCENTAGE", "Config");

        if (isEnrolled) {
            payroll.addExtraComponent("SSF (11% Contribution)", ssfContribution, "DEDUCTION", "PERCENTAGE", "Statutory");
        } else {
            payroll.addExtraComponent("SSF (Status)", 0.0, "INFO", "FIXED", "Unenrolled - 1% Tax Applies");
        }

        if (festivalBonus > 0) payroll.addExtraComponent("Festival Bonus", festivalBonus, "EARNING", "MANUAL", "Input");
        if (otherBonuses > 0) payroll.addExtraComponent("Other Bonuses", otherBonuses, "EARNING", "MANUAL", "Input");
        if (citContribution > 0) payroll.addExtraComponent("CIT Contribution", citContribution, "DEDUCTION", "MANUAL", "Input");

        // 5. PROCESS DYNAMIC ADJUSTMENTS
        double dynamicEarnings = 0.0;
        double dynamicDeductions = 0.0;
        if (payload.get("extraComponents") instanceof List<?> extras) {
            for (Object obj : extras) {
                if (obj instanceof Map<?, ?> comp) {
                    String label = String.valueOf(comp.get("label"));
                    double amt = Double.parseDouble(comp.get("amount").toString());
                    String type = (comp.get("type") != null) ? comp.get("type").toString() : "EARNING";
                    payroll.addExtraComponent(label, amt, type, "ADJUSTMENT", "Manual");
                    if ("EARNING".equalsIgnoreCase(type)) dynamicEarnings += amt;
                    else dynamicDeductions += amt;
                }
            }
        }

        // 6. TOTALS & TAXATION
        double totalAllowances = dearnessAmt + calculatedHra + dynamicEarnings;
        double monthlyGross = finalEarnedSalary + totalAllowances + festivalBonus + otherBonuses;
        double taxableMonthly = monthlyGross - (ssfContribution + citContribution);

        double annualTax = calculateNepalTax(taxableMonthly * 12, employee.getMaritalStatus(), isEnrolled);
        double monthlyTax = round(annualTax / 12);

        payroll.setTotalAllowances(round(totalAllowances));
        payroll.setFestivalBonus(festivalBonus);
        payroll.setOtherBonuses(otherBonuses);
        payroll.setCitContribution(citContribution);
        payroll.setGrossSalary(round(monthlyGross));
        payroll.setTaxableIncome(round(taxableMonthly));
        payroll.setTotalTax(monthlyTax);

        double totalDeductions = ssfContribution + citContribution + monthlyTax + dynamicDeductions;
        payroll.setTotalDeductions(round(totalDeductions));
        payroll.setNetSalary(round(monthlyGross - totalDeductions));

        String remarkSource = (payload.get("earnedSalary") != null) ? "Manual Entry" : "Attendance Based";
        String ssfNote = isEnrolled ? "SSF Enrolled (Tax-Free 1st Slab)" : "Unenrolled (1% Social Security Tax)";
        payroll.setRemarks(String.format("Basis: %s | %s", remarkSource, ssfNote));

        return payroll;
    }

    private double calculateNepalTax(double annualTaxable, String status, boolean isSsfEnrolled) {
        if (annualTaxable <= 0) return 0.0;
        List<TaxSlab> slabs = taxSlabRepo.findByTaxpayerStatusOrderByMinAmountAsc(status);
        double totalTax = 0.0;

        for (TaxSlab slab : slabs) {
            double prevLimit = slab.getPreviousLimit();
            if (annualTaxable > prevLimit) {
                double bucket = Math.min(annualTaxable, slab.getMaxAmount()) - prevLimit;
                if (bucket > 0) {
                    double rate;
                    if (slab.getMinAmount() == 0 && isSsfEnrolled) {
                        rate = 0.0;
                    } else {
                        rate = (slab.getRatePercentage() / 100.0);
                    }
                    totalTax += bucket * rate;
                }
            }
        }
        return totalTax;
    }

    private double countAttendanceDaysInternal(Integer empId, LocalDate start, LocalDate end) {
        return attendanceRepo.findByEmployee_EmpIdAndAttendanceDateGreaterThanEqualAndAttendanceDateLessThan(empId, start, end)
                .stream().map(Attendance::getAttendanceDate).distinct().count();
    }

    private double calculatePaidLeaveDaysInternal(Integer empId, LocalDate start, LocalDate end) {
        LocalDate actualEnd = end.minusDays(1);
        return employeeLeaveRepo.findRelevantLeaves(empId, "Approved", start, actualEnd).stream()
                .filter(l -> l.getLeaveType() != null && Boolean.TRUE.equals(l.getLeaveType().getPaid()))
                .mapToDouble(l -> {
                    LocalDate overlapStart = l.getStartDate().isBefore(start) ? start : l.getStartDate();
                    LocalDate overlapEnd = l.getEndDate().isAfter(actualEnd) ? actualEnd : l.getEndDate();
                    return Math.max(0, ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1);
                }).sum();
    }

    private double countSaturdaysInPeriod(LocalDate start, LocalDate end) {
        double count = 0; LocalDate current = start;
        while (current.isBefore(end)) {
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY) count++;
            current = current.plusDays(1);
        }
        return count;
    }

    private double countPublicHolidaysInPeriod(LocalDate start, LocalDate end) {
        return holidayRepo.findByHolidayDateBetween(start, end.minusDays(1)).stream()
                .filter(h -> h.getHolidayDate().getDayOfWeek() != DayOfWeek.SATURDAY).count();
    }

    @Override
    @Transactional
    public Payroll processPayroll(Map<String, Object> payload) {
        Payroll payroll = calculatePreview(payload);
        Employee employee = payroll.getEmployee();

        payrollRepo.findByEmployeeEmpId(employee.getEmpId()).stream()
                .filter(p -> "PENDING_PAYMENT".equals(p.getStatus()) && p.getPayPeriodStart().equals(payroll.getPayPeriodStart()))
                .forEach(payrollRepo::delete);

        BankAccount bank = employee.getPrimaryBankAccount();
        if (bank == null && !employee.getBankAccount().isEmpty()) bank = employee.getBankAccount().get(0);
        if (bank == null) throw new RuntimeException("Bank Account missing.");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String principalName = (auth != null) ? auth.getName() : "system";
        User loggedInUser = userRepo.findByEmail(principalName).or(() -> userRepo.findByUsername(principalName)).orElseThrow();

        LocalDate now = LocalDate.now();
        PayGroup effectiveGroup = resolvePayGroup(employee);
        Integer targetGroupId = effectiveGroup.getPayGroupId();

        MonthlyInfo summary = monthlyInfoRepo.findByMonthNameAndStatus(now.getMonth().name(), "PROCESSING").stream()
                .filter(m -> m.getPayGroup() != null && m.getPayGroup().getPayGroupId().equals(targetGroupId))
                .findFirst()
                .orElseGet(() -> createNewMonthlyBatch(employee, now, loggedInUser));

        Object pmId = payload.get("paymentMethodId");
        PaymentMethod selectedMethod = paymentMethodRepo.findById(pmId != null ? Integer.valueOf(pmId.toString()) : 1).orElseThrow();

        payroll.setMonthlyInfo(summary);
        payroll.setStatus("PENDING_PAYMENT");
        payroll.setProcessedBy(loggedInUser);
        payroll.setPaymentAccount(bank);
        payroll.setPaymentMethod(selectedMethod);
        payroll.setPayDate(now);

        return payrollRepo.save(payroll);
    }

    @Override
    @Transactional
    public void finalizePayroll(Integer id, String ref) {
        Payroll p = payrollRepo.findById(id).orElseThrow();
        if ("PAID".equals(p.getStatus())) return;
        p.setStatus("PAID");
        p.setTransactionRef(ref);
        p.setProcessedAt(LocalDateTime.now());
        updateMonthlyTotals(p.getMonthlyInfo(), p);
        payrollRepo.save(p);
    }

    @Override @Transactional public void rollbackPayroll(Integer id) { payrollRepo.deleteById(id); }
    @Override public List<Payroll> getAllPayrolls() { return payrollRepo.findAll(); }
    @Override public List<Payroll> getPayrollByEmployeeId(Integer id) { return payrollRepo.findByEmployeeEmpId(id); }
    @Override public Payroll getPayrollById(Integer id) { return payrollRepo.findById(id).orElseThrow(); }
    @Override public Payroll voidPayroll(Integer id) { return updateStatus(id, "VOIDED"); }
    @Override public Payroll updateStatus(Integer id, String status) {
        Payroll p = getPayrollById(id);
        p.setStatus(status);
        if ("VOIDED".equals(status)) p.setIsVoided(true);
        return payrollRepo.save(p);
    }

    private PayGroup resolvePayGroup(Employee emp) {
        if (emp.getPayGroup() != null) return emp.getPayGroup();
        return payGroupRepo.findById(4).orElseThrow(() -> new RuntimeException("Default PayGroup 4 missing."));
    }

    private MonthlyInfo createNewMonthlyBatch(Employee emp, LocalDate date, User creator) {
        PayGroup group = resolvePayGroup(emp);
        return monthlyInfoRepo.save(MonthlyInfo.builder()
                .monthName(date.getMonth().name()).monthStart(date.withDayOfMonth(1))
                .monthEnd(date.withDayOfMonth(date.lengthOfMonth())).payGroup(group).totalEmployeesProcessed(0)
                .totalGrossSalary(0.0).totalAllowances(0.0).totalDeductions(0.0).totalTax(0.0).totalNetSalary(0.0)
                .currency("NPR").status("PROCESSING").generatedBy(creator).generatedAt(LocalDateTime.now()).build());
    }

    private void updateMonthlyTotals(MonthlyInfo summary, Payroll p) {
        summary.setTotalEmployeesProcessed((summary.getTotalEmployeesProcessed() == null ? 0 : summary.getTotalEmployeesProcessed()) + 1);
        summary.setTotalGrossSalary((summary.getTotalGrossSalary() == null ? 0.0 : summary.getTotalGrossSalary()) + p.getGrossSalary());
        summary.setTotalNetSalary((summary.getTotalNetSalary() == null ? 0.0 : summary.getTotalNetSalary()) + p.getNetSalary());
        monthlyInfoRepo.save(summary);
    }

    private void validatePayrollPeriod(Integer empId, LocalDate start) {
        boolean exists = payrollRepo.findByEmployeeEmpId(empId).stream()
                .anyMatch(p -> !"VOIDED".equals(p.getStatus()) && p.getPayPeriodStart().equals(start)
                        && ("PAID".equals(p.getStatus()) || "PROCESSING".equals(p.getStatus())));
        if (exists) throw new RuntimeException("Payroll already processed for this period.");
    }

    private double getComponentDefault(List<SalaryComponent> components, String name, double fallback) {
        return components.stream()
                .filter(c -> c.getComponentName().toLowerCase().contains(name.toLowerCase()))
                .mapToDouble(SalaryComponent::getDefaultValue).findFirst().orElse(fallback);
    }

    private Integer resolveEmpId(Map<String, Object> payload) {
        Object id = payload.get("empId");
        if (id == null && payload.get("employee") instanceof Map) id = ((Map<?, ?>) payload.get("employee")).get("empId");
        if (id == null || id.toString().equalsIgnoreCase("undefined")) throw new RuntimeException("Employee ID missing.");
        return Double.valueOf(id.toString()).intValue();
    }

    private int parseMonthValue(String month) {
        try {
            if (month.matches("\\d+")) return Integer.parseInt(month);
            return java.time.Month.valueOf(month.toUpperCase()).getValue();
        } catch (Exception e) { return -1; }
    }

    private double round(double val) { return Math.round(val * 100.0) / 100.0; }
    private double parseDouble(Map<String, Object> p, String k) { Object val = p.get(k); return (val == null) ? 0.0 : Double.parseDouble(val.toString()); }
    private double getFallbackBasicFromComponents() { return salaryComponentRepo.findAll().stream().filter(c -> c.getComponentName().equalsIgnoreCase("Basic Salary")).mapToDouble(SalaryComponent::getDefaultValue).findFirst().orElse(0.0); }
}