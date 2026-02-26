package np.edu.nast.payroll.Payroll.service.impl;

import np.edu.nast.payroll.Payroll.entity.EmployeeDocument;
import np.edu.nast.payroll.Payroll.entity.Employee;
import np.edu.nast.payroll.Payroll.entity.User;
import np.edu.nast.payroll.Payroll.exception.ResourceNotFoundException;
import np.edu.nast.payroll.Payroll.repository.EmployeeDocumentRepository;
import np.edu.nast.payroll.Payroll.repository.EmployeeRepository;
import np.edu.nast.payroll.Payroll.repository.UserRepository;
import np.edu.nast.payroll.Payroll.service.EmployeeDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EmployeeDocumentServiceImpl implements EmployeeDocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    private final Path uploadDir = Paths.get("uploads/documents");

    public EmployeeDocumentServiceImpl(EmployeeDocumentRepository documentRepository,
                                       EmployeeRepository employeeRepository,
                                       UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    @Override
    public EmployeeDocument uploadDocument(MultipartFile file, Integer empId,
                                           String documentType, String title,
                                           Integer uploadedByUserId) throws IOException {
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (!List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw new IllegalArgumentException("Only PDF, JPG, PNG allowed");
        }

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String filename = "doc_" + empId + "_" + System.currentTimeMillis() + ext;

        Path filePath = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + empId));

        User uploadedBy = new User();
        uploadedBy.setUserId(uploadedByUserId);

        EmployeeDocument doc = EmployeeDocument.builder()
                .employee(employee)
                .documentType(documentType)
                .title(title)
                .issueDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(1))
                .filePath(filename)
                .contentType(contentType)
                .uploadedBy(uploadedBy)
                .status("PENDING") // ✅ starts as PENDING (draft)
                .build();

        return documentRepository.save(doc);
    }

    @Override
    public ResponseEntity<Resource> getFile(Integer id) {
        EmployeeDocument doc = getDocumentById(id);
        try {
            Path filePath = uploadDir.resolve(doc.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(doc.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + doc.getFilePath() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    public EmployeeDocument saveDocument(EmployeeDocument document) {
        if (document.getEmployee() == null || document.getEmployee().getEmpId() == null) {
            throw new IllegalArgumentException("Employee ID must not be null");
        }
        Employee employee = employeeRepository.findById(document.getEmployee().getEmpId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with ID: " + document.getEmployee().getEmpId()));
        document.setEmployee(employee);
        return documentRepository.save(document);
    }

    @Override
    public List<EmployeeDocument> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Override
    public List<EmployeeDocument> getAllDocumentsByStatus(String status) {
        if (status == null || status.equalsIgnoreCase("ALL")) {
            return documentRepository.findAll();
        }
        return documentRepository.findByStatus(status.toUpperCase());
    }

    @Override
    public EmployeeDocument getDocumentById(Integer id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));
    }

    @Override
    public void deleteDocument(Integer id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found with ID: " + id);
        }
        EmployeeDocument doc = getDocumentById(id);
        try {
            Files.deleteIfExists(uploadDir.resolve(doc.getFilePath()));
        } catch (IOException e) {
            System.err.println("Could not delete file: " + e.getMessage());
        }
        documentRepository.deleteById(id);
    }

    // ✅ ADMIN — approve or reject
    @Override
    public EmployeeDocument reviewDocument(Integer documentId, String status,
                                           String reviewNote, Integer reviewedByUserId) {
        EmployeeDocument doc = getDocumentById(documentId);

        User reviewer = userRepository.findById(reviewedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + reviewedByUserId));

        doc.setStatus(status.toUpperCase());
        doc.setReviewedBy(reviewer);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewNote(reviewNote);

        return documentRepository.save(doc);
    }

    // ✅ EMPLOYEE — submit all PENDING docs → change to SUBMITTED
    @Override
    public void submitForReview(Integer empId) {
        List<EmployeeDocument> docs = documentRepository.findByEmployee_EmpId(empId);
        docs.stream()
                .filter(d -> "PENDING".equals(d.getStatus()))
                .forEach(d -> {
                    d.setStatus("SUBMITTED");
                    documentRepository.save(d);
                });
    }
}