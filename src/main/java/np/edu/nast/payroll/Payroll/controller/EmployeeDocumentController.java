package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.entity.EmployeeDocument;
import np.edu.nast.payroll.Payroll.service.EmployeeDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/employee-documents")
public class EmployeeDocumentController {

    @Autowired
    private EmployeeDocumentService service;

    @PostMapping
    public EmployeeDocument createDocument(@RequestBody EmployeeDocument document) {
        return service.saveDocument(document);
    }

    @GetMapping
    public List<EmployeeDocument> getAllDocuments() {
        return service.getAllDocuments();
    }

    @GetMapping("/{id}")
    public EmployeeDocument getDocument(@PathVariable Integer id) {
        return service.getDocumentById(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Integer id) {
        return service.getFile(id);
    }

    @PostMapping("/upload")
    public ResponseEntity<EmployeeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("empId") Integer empId,
            @RequestParam("documentType") String documentType,
            @RequestParam("title") String title,
            @RequestParam("uploadedBy") Integer uploadedByUserId) {
        try {
            EmployeeDocument doc = service.uploadDocument(file, empId, documentType, title, uploadedByUserId);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable Integer id) {
        service.deleteDocument(id);
    }

    // ✅ ADMIN — get all documents filtered by status
    @GetMapping("/admin/all")
    public ResponseEntity<List<EmployeeDocument>> getAllDocumentsForAdmin(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.getAllDocumentsByStatus(status));
    }

    // ✅ ADMIN — approve or reject a document
    @PatchMapping("/admin/{documentId}/review")
    public ResponseEntity<EmployeeDocument> reviewDocument(
            @PathVariable Integer documentId,
            @RequestBody Map<String, String> body) {
        try {
            String status = body.get("status");
            String reviewNote = body.get("reviewNote");
            Integer reviewedByUserId = Integer.parseInt(body.get("reviewedBy"));
            EmployeeDocument updated = service.reviewDocument(documentId, status, reviewNote, reviewedByUserId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ✅ EMPLOYEE — submit all PENDING docs for review
    @PostMapping("/submit-review/{empId}")
    public ResponseEntity<String> submitForReview(@PathVariable Integer empId) {
        try {
            service.submitForReview(empId);
            return ResponseEntity.ok("Documents submitted for review");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}