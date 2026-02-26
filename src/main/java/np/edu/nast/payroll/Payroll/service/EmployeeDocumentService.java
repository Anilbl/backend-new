package np.edu.nast.payroll.Payroll.service;

import np.edu.nast.payroll.Payroll.entity.EmployeeDocument;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface EmployeeDocumentService {
    EmployeeDocument saveDocument(EmployeeDocument document);
    List<EmployeeDocument> getAllDocuments();
    List<EmployeeDocument> getAllDocumentsByStatus(String status);
    EmployeeDocument getDocumentById(Integer id);
    void deleteDocument(Integer id);
    EmployeeDocument uploadDocument(MultipartFile file, Integer empId,
                                    String documentType, String title,
                                    Integer uploadedByUserId) throws IOException;
    ResponseEntity<Resource> getFile(Integer id);
    EmployeeDocument reviewDocument(Integer documentId, String status,
                                    String reviewNote, Integer reviewedByUserId);
    void submitForReview(Integer empId); // ✅ NEW
}