package np.edu.nast.payroll.Payroll.service;

import np.edu.nast.payroll.Payroll.dto.CommandCenterDTO;
import np.edu.nast.payroll.Payroll.dto.EmployeePayrollHistoryDTO;
import np.edu.nast.payroll.Payroll.dto.PayrollDashboardDTO;
import np.edu.nast.payroll.Payroll.entity.Payroll;
import np.edu.nast.payroll.Payroll.reportdto.PayrollSummaryDTO;

import java.util.List;
import java.util.Map;

public interface PayrollService {
    List<Payroll> getAllPayrolls();

    // Core Engine Methods
    Payroll calculatePreview(Map<String, Object> payload);
    Payroll processPayroll(Map<String, Object> payload);
    void finalizePayroll(Integer payrollId, String transactionRef);
    void rollbackPayroll(Integer payrollId);

    // Fetching & Queries
    // Use this as the main history fetcher for the React Audit Modal
    List<EmployeePayrollHistoryDTO> getPayrollByEmployeeId(Integer empId);

    Payroll updateStatus(Integer id, String status);
    Payroll voidPayroll(Integer id);
    Payroll getPayrollById(Integer id);

    // Dashboard & Command Center Logic
    List<PayrollDashboardDTO> getBatchCalculation(String month, int year);
    PayrollSummaryDTO getSalarySummary(int month, int year);
    CommandCenterDTO getCommandCenterData(int month, int year);
}