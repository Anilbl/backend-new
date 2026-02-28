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
    public List<EmployeePayrollHistoryDTO> getPayrollByEmployeeId(Integer id) {
        log.info("DEBUG: Fetching audit history for Emp ID: {}", id);
        List<Payroll> history = payrollRepo.findHistoryByEmployeeId(id);

        if (history == null) return new ArrayList<>();

        return history.stream().map(p -> {
            // Logic to find SSF from components if the direct column is empty
            double ssf = (p.getSsfContribution() != null) ? p.getSsfContribution() :
                    findComponentAmount(p, "SSF Contribution", 0.0);

            return EmployeePayrollHistoryDTO.builder()
                    .payrollId(p.getPayrollId()) // p.getPayrollId() is Integer
                    .payPeriodStart(p.getPayPeriodStart())
                    .payDate(p.getPayDate())
                    .grossSalary(p.getGrossSalary())
                    .ssfContribution(ssf)
                    .totalTax(p.getTotalTax())
                    .netSalary(p.getNetSalary())
                    .status(p.getStatus())
                    .build();
        }).collect(Collectors.toList());
    }

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
        summary.setTotalEmployees(employeeRepo.findAll().stream().filter(e -> Boolean.TRUE.equals(e.getIsActive())).count());
        return summary;
    }

    @Override
    public List<PayrollDashboardDTO> getBatchCalculation(String month, int year) {
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

    @Override
    public CommandCenterDTO getCommandCenterData(int month, int year) {
        LocalDate periodStart = LocalDate.of(year, month, 1);
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

        List<SalaryComponent> allComponents = salaryComponentRepo.findAll();

        List<EmployeePayrollRowDTO> rows = activeEmployees.stream().map(emp -> {
            Payroll existing = payrollMap.get(emp.getEmpId());
            PayrollDashboardDTO preview = previewMap.get(emp.getEmpId());

            if (existing != null) {
                List<Map<String, Object>> extraCompsDto = new ArrayList<>();
                if (existing.getExtraComponents() != null) {
                    for (Object ec : existing.getExtraComponents()) {
                        String name = getComponentName(ec);
                        if (name.contains("Basic Salary") || name.contains("SSF") || name.contains("Rent") || name.contains("Dearness")) continue;

                        Map<String, Object> m = new HashMap<>();
                        m.put("label", name);
                        m.put("amount", getComponentAmount(ec));
                        m.put("type", getComponentCategory(ec));
                        extraCompsDto.add(m);
                    }
                }

                return EmployeePayrollRowDTO.builder()
                        .empId(emp.getEmpId())
                        .fullName(emp.getFirstName() + " " + emp.getLastName())
                        .basicSalary(emp.getBasicSalary())
                        .earnedSalary(existing.getBasicSalary())
                        .ssfContribution(existing.getSsfContribution())
                        .houseRentAllowance(findComponentAmount(existing, "House Rent Allowance", 0.0))
                        .dearnessAllowance(findComponentAmount(existing, "Dearness Allowance", 0.0))
                        .extraComponents(extraCompsDto)
                        .payrollId(existing.getPayrollId())
                        .status(existing.getStatus())
                        .isActive(emp.getIsActive())
                        .build();
            } else {
                double earned = preview != null ? preview.getEarnedSalary() : (emp.getBasicSalary() != null ? emp.getBasicSalary() : 0.0);
                double base = emp.getBasicSalary() != null ? emp.getBasicSalary() : 0.0;

                double rent = calculateComponentValue(allComponents, "House Rent Allowance", base);
                double dearness = calculateComponentValue(allComponents, "Dearness Allowance", base);
                double ssf = Boolean.TRUE.equals(emp.getIsSsfEnrolled()) ?
                        calculateComponentValue(allComponents, "SSF Contribution", base) : 0.0;

                return EmployeePayrollRowDTO.builder()
                        .empId(emp.getEmpId())
                        .fullName(emp.getFirstName() + " " + emp.getLastName())
                        .basicSalary(base)
                        .earnedSalary(earned)
                        .ssfContribution(round(ssf))
                        .houseRentAllowance(round(rent))
                        .dearnessAllowance(round(dearness))
                        .extraComponents(new ArrayList<>())
                        .payrollId(null)
                        .status(earned > 0 ? "READY" : "NO_EARNINGS")
                        .isActive(emp.getIsActive())
                        .build();
            }
        }).collect(Collectors.toList());

        CommandCenterDTO dto = new CommandCenterDTO();
        dto.setEmployeeRows(rows);
        dto.setMonthlyPayrollTotal(dbPayrolls.stream().filter(p -> "PAID".equals(p.getStatus())).mapToDouble(Payroll::getNetSalary).sum());
        dto.setPendingVerifications((int) rows.stream().filter(r -> "READY".equals(r.getStatus()) || "PENDING_PAYMENT".equals(r.getStatus())).count());
        dto.setPayrollStatus(rows.stream().anyMatch(r -> "PAID".equals(r.getStatus())) ? "Processing" : "Idle");
        dto.setCompliancePercentage(100);
        return dto;
    }

    @Override
    public Payroll calculatePreview(Map<String, Object> payload) {
        Integer empId = resolveEmpId(payload);
        Employee employee = employeeRepo.findById(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        int year = (payload.get("year") != null) ? Integer.parseInt(payload.get("year").toString()) : LocalDate.now().getYear();
        int monthValue = (payload.get("month") != null) ? parseMonthValue(payload.get("month").toString()) : LocalDate.now().getMonthValue();
        LocalDate periodStart = LocalDate.of(year, monthValue, 1);

        validatePayrollPeriod(empId, periodStart);

        double earnedSalary = parseDouble(payload, "earnedSalary");
        double baseSalary = employee.getBasicSalary() != null ? employee.getBasicSalary() : 0.0;

        double ssfContribution = Boolean.TRUE.equals(employee.getIsSsfEnrolled()) ?
                calculateComponentValue(salaryComponentRepo.findAll(), "SSF Contribution", baseSalary) : 0.0;

        double houseRent = parseDouble(payload, "houseRentAllowance");
        double dearness = parseDouble(payload, "dearnessAllowance");

        Payroll payroll = Payroll.builder()
                .employee(employee)
                .payGroup(resolvePayGroup(employee))
                .basicSalary(round(earnedSalary))
                .ssfContribution(round(ssfContribution))
                .payPeriodStart(periodStart)
                .payPeriodEnd(periodStart.plusMonths(1).minusDays(1))
                .status("PREVIEW")
                .extraComponents(new ArrayList<>())
                .build();

        payroll.addExtraComponent("Earned Basic Salary", round(earnedSalary), "EARNING", "FIXED", "Attendance");
        if (dearness > 0) payroll.addExtraComponent("Dearness Allowance", round(dearness), "EARNING", "FIXED", "Adjustment");
        if (houseRent > 0) payroll.addExtraComponent("House Rent Allowance", round(houseRent), "EARNING", "FIXED", "Adjustment");
        if (ssfContribution > 0) payroll.addExtraComponent("SSF Contribution", round(ssfContribution), "DEDUCTION", "STATUTORY", "Adjustment");

        double dynamicEarnings = 0.0;
        double dynamicDeductions = 0.0;
        if (payload.get("extraComponents") instanceof List<?> extras) {
            for (Object obj : extras) {
                Map<?, ?> comp = (Map<?, ?>) obj;
                String label = String.valueOf(comp.get("label"));
                if (label.contains("SSF") || label.contains("Rent") || label.contains("Dearness")) continue;

                double amt = Double.parseDouble(comp.get("amount").toString());
                String type = comp.get("type") != null ? comp.get("type").toString() : "EARNING";

                payroll.addExtraComponent(label, amt, type, "ADJUSTMENT", "Manual");
                if ("EARNING".equalsIgnoreCase(type)) dynamicEarnings += amt;
                else dynamicDeductions += amt;
            }
        }

        double totalAllowances = dearness + houseRent + dynamicEarnings;
        double monthlyGross = earnedSalary + totalAllowances;
        double taxableMonthly = monthlyGross - ssfContribution;

        double annualTax = calculateNepalTax(taxableMonthly * 12, employee.getMaritalStatus(), employee.getIsSsfEnrolled());
        double monthlyTax = round(annualTax / 12);

        payroll.setTotalAllowances(round(totalAllowances));
        payroll.setGrossSalary(round(monthlyGross));
        payroll.setTaxableIncome(round(taxableMonthly));
        payroll.setTotalTax(monthlyTax);

        double totalDeductions = ssfContribution + monthlyTax + dynamicDeductions;
        payroll.setTotalDeductions(round(totalDeductions));
        payroll.setNetSalary(round(monthlyGross - totalDeductions));

        return payroll;
    }

    private double calculateComponentValue(List<SalaryComponent> components, String name, double salaryBase) {
        return components.stream()
                .filter(c -> c.getComponentName().equalsIgnoreCase(name))
                .findFirst()
                .map(c -> "PERCENTAGE_OF_BASIC".equalsIgnoreCase(c.getCalculationMethod()) ?
                        salaryBase * (c.getDefaultValue() / 100.0) : c.getDefaultValue())
                .orElse(0.0);
    }

    private double findComponentAmount(Payroll payroll, String label, double fallback) {
        if (payroll.getExtraComponents() == null) return fallback;
        return payroll.getExtraComponents().stream()
                .filter(c -> label.equalsIgnoreCase(getComponentName(c)))
                .mapToDouble(this::getComponentAmount)
                .findFirst().orElse(fallback);
    }

    private String getComponentName(Object component) {
        try { return (String) component.getClass().getMethod("getComponentName").invoke(component); }
        catch (Exception e) { return ""; }
    }
    private Double getComponentAmount(Object component) {
        try { return (Double) component.getClass().getMethod("getAmount").invoke(component); }
        catch (Exception e) { return 0.0; }
    }
    private String getComponentCategory(Object component) {
        try { return (String) component.getClass().getMethod("getCategory").invoke(component); }
        catch (Exception e) { return "EARNING"; }
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
                    double rate = (slab.getMinAmount() == 0 && isSsfEnrolled) ? 0.0 : (slab.getRatePercentage() / 100.0);
                    totalTax += bucket * rate;
                }
            }
        }
        return totalTax;
    }

    private double countAttendanceDaysInternal(Integer empId, LocalDate start, LocalDate end) {
        return attendanceRepo.findByEmployee_EmpIdAndAttendanceDateGreaterThanEqualAndAttendanceDateLessThan(empId, start, end).stream().map(Attendance::getAttendanceDate).distinct().count();
    }
    private double calculatePaidLeaveDaysInternal(Integer empId, LocalDate start, LocalDate end) {
        LocalDate actualEnd = end.minusDays(1);
        return employeeLeaveRepo.findRelevantLeaves(empId, "Approved", start, actualEnd).stream().filter(l -> l.getLeaveType() != null && Boolean.TRUE.equals(l.getLeaveType().getPaid())).mapToDouble(l -> {
            LocalDate overlapStart = l.getStartDate().isBefore(start) ? start : l.getStartDate();
            LocalDate overlapEnd = l.getEndDate().isAfter(actualEnd) ? actualEnd : l.getEndDate();
            return Math.max(0, ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1);
        }).sum();
    }
    private double countSaturdaysInPeriod(LocalDate start, LocalDate end) {
        double count = 0; LocalDate current = start;
        while (current.isBefore(end)) { if (current.getDayOfWeek() == DayOfWeek.SATURDAY) count++; current = current.plusDays(1); }
        return count;
    }
    private double countPublicHolidaysInPeriod(LocalDate start, LocalDate end) {
        return holidayRepo.findByHolidayDateBetween(start, end.minusDays(1)).stream().filter(h -> h.getHolidayDate().getDayOfWeek() != DayOfWeek.SATURDAY).count();
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
        MonthlyInfo summary = monthlyInfoRepo.findByMonthNameAndStatus(now.getMonth().name(), "PROCESSING").stream().findFirst()
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

    @Override @Transactional public void finalizePayroll(Integer id, String ref) {
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
        return payGroupRepo.findById(4).orElseThrow(() -> new RuntimeException("Default PayGroup missing."));
    }

    private MonthlyInfo createNewMonthlyBatch(Employee emp, LocalDate date, User creator) {
        return monthlyInfoRepo.save(MonthlyInfo.builder()
                .monthName(date.getMonth().name())
                .monthStart(date.withDayOfMonth(1))
                .monthEnd(date.withDayOfMonth(date.lengthOfMonth()))
                .payGroup(resolvePayGroup(emp))
                .totalEmployeesProcessed(0).totalGrossSalary(0.0).totalNetSalary(0.0).currency("NPR")
                .status("PROCESSING").generatedBy(creator).generatedAt(LocalDateTime.now()).build());
    }

    private void updateMonthlyTotals(MonthlyInfo summary, Payroll p) {
        summary.setTotalEmployeesProcessed((summary.getTotalEmployeesProcessed() == null ? 0 : summary.getTotalEmployeesProcessed()) + 1);
        summary.setTotalGrossSalary((summary.getTotalGrossSalary() == null ? 0.0 : summary.getTotalGrossSalary()) + p.getGrossSalary());
        summary.setTotalNetSalary((summary.getTotalNetSalary() == null ? 0.0 : summary.getTotalNetSalary()) + p.getNetSalary());
        monthlyInfoRepo.save(summary);
    }

    private void validatePayrollPeriod(Integer empId, LocalDate start) {
        boolean exists = payrollRepo.findByEmployeeEmpId(empId).stream()
                .anyMatch(p -> "PAID".equals(p.getStatus()) && p.getPayPeriodStart().equals(start));
        if (exists) throw new RuntimeException("Payroll already exists for this period.");
    }

    private Integer resolveEmpId(Map<String, Object> payload) {
        Object id = payload.get("empId");
        if (id == null) throw new RuntimeException("Employee ID missing.");
        return Double.valueOf(id.toString()).intValue();
    }

    private int parseMonthValue(String month) {
        try { if (month.matches("\\d+")) return Integer.parseInt(month); return java.time.Month.valueOf(month.toUpperCase()).getValue(); }
        catch (Exception e) { return -1; }
    }
    private double round(double val) { return Math.round(val * 100.0) / 100.0; }
    private double parseDouble(Map<String, Object> p, String k) {
        Object val = p.get(k);
        return (val == null) ? 0.0 : Double.parseDouble(val.toString());
    }
    private double getFallbackBasicFromComponents() {
        return salaryComponentRepo.findAll().stream()
                .filter(c -> c.getComponentName().equalsIgnoreCase("Basic Salary"))
                .mapToDouble(SalaryComponent::getDefaultValue).findFirst().orElse(0.0);
    }
}