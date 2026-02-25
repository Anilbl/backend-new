package np.edu.nast.payroll.Payroll.service.impl;

import np.edu.nast.payroll.Payroll.entity.SalaryComponentType;
import np.edu.nast.payroll.Payroll.repository.SalaryComponentTypeRepository;
import np.edu.nast.payroll.Payroll.service.SalaryComponentTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalaryComponentTypeServiceImpl implements SalaryComponentTypeService {

    private final SalaryComponentTypeRepository repo;

    public SalaryComponentTypeServiceImpl(SalaryComponentTypeRepository repo) {
        this.repo = repo;
    }

    @Override
    public SalaryComponentType create(SalaryComponentType type) {
        return repo.save(type);
    }

    @Override
    public SalaryComponentType update(Long id, SalaryComponentType type) {
        SalaryComponentType existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("SalaryComponentType not found"));

        existing.setName(type.getName());
        existing.setDescription(type.getDescription());

        return repo.save(existing);
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }

    @Override
    public SalaryComponentType getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("SalaryComponentType not found"));
    }

    @Override
    public List<SalaryComponentType> getAll() {
        return repo.findAll();
    }
}