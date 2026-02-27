package np.edu.nast.payroll.Payroll.service.impl;

import np.edu.nast.payroll.Payroll.entity.BankAccount;
import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.Payroll;
import np.edu.nast.payroll.Payroll.repository.BankAccountRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeRepository;
import np.edu.nast.payroll.Payroll.repository.PayrollRepository;
import np.edu.nast.payroll.Payroll.salaryDTO.SalaryAnalyticsResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SalaryAnalyticsService {
    @Autowired private EmployeeRepository employeeRepo;
    @Autowired private PayrollRepository payrollRepo;
    @Autowired private BankAccountRepository bankAccountRepo;

    public SalaryAnalyticsResponseDTO getSalaryDetailsByUsername(String loginName, String monthStr) {
        // 1. Find Employee by username
        Employee emp = employeeRepo.findByUser_Username(loginName)
                .orElseThrow(() -> new RuntimeException("Employee profile not found for user: " + loginName));

        // 2. Parse "YYYY-MM" from frontend (e.g., "2026-01")
        String[] parts = monthStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        // 3. Find the Payroll record
        Payroll payroll = payrollRepo.findByEmployeeEmpIdAndMonth(emp.getEmpId(), year, month)
                .orElseThrow(() -> new RuntimeException("No payroll record found for " + monthStr));

        // 4. Find the Primary Bank Details
        BankAccount bankDetails = bankAccountRepo.findByEmployeeEmpIdAndIsPrimaryTrue(emp.getEmpId())
                .orElse(null);

        // 5. Map to DTO
        return mapToDTO(emp, payroll, bankDetails);
    }

    private SalaryAnalyticsResponseDTO mapToDTO(Employee emp, Payroll payroll, BankAccount bank) {
        SalaryAnalyticsResponseDTO dto = new SalaryAnalyticsResponseDTO();

        // Employee/Bank Info
        dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
        dto.setDesignation(emp.getPosition() != null ? emp.getPosition().getDesignationTitle() : "N/A");
        dto.setEmploymentStatus(emp.getEmploymentStatus());

        if (bank != null && bank.getBank() != null) {
            dto.setBankName(bank.getBank().getBankName());

            // Logic Change: Convert Long account number to String for the DTO
            if (bank.getAccountNumber() != null) {
                dto.setBankAccount(String.valueOf(bank.getAccountNumber()));
            } else {
                dto.setBankAccount("N/A");
            }

        } else {
            dto.setBankName("N/A");
            dto.setBankAccount("N/A");
        }

        // Financial Breakdown (Ensuring frontend gets the exact numbers it needs)
        dto.setBaseSalary(emp.getBasicSalary());
        dto.setGrossSalary(payroll.getGrossSalary());
        dto.setTotalAllowances(payroll.getTotalAllowances());
        dto.setTotalDeductions(payroll.getTotalDeductions());

        // Frontend uses 'taxableAmount' to display the "Tax (TDS)" deduction
        dto.setTaxableAmount(payroll.getTotalTax());

        // Net Salary as calculated by the system
        dto.setNetSalary(payroll.getNetSalary());

        return dto;
    }
}