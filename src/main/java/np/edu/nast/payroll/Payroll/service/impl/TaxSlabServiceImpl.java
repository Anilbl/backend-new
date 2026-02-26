package np.edu.nast.payroll.Payroll.service.impl;

import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.entity.TaxSlab;
import np.edu.nast.payroll.Payroll.entity.User;
import np.edu.nast.payroll.Payroll.repository.TaxSlabRepository;
import np.edu.nast.payroll.Payroll.repository.UserRepository; // Added
import np.edu.nast.payroll.Payroll.service.TaxSlabService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor // Automatically creates constructor for final fields
public class TaxSlabServiceImpl implements TaxSlabService {

    private final TaxSlabRepository repo;
    private final UserRepository userRepo; // Added to fetch User objects

    @Override
    @Transactional
    public TaxSlab create(TaxSlab slab, Integer userId) {
        // Fetch the user from database to establish relationship
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        slab.setCreatedBy(user);
        slab.setUpdatedBy(user); // Initially, creator is also the last updater
        return repo.save(slab);
    }

    @Override
    @Transactional
    public TaxSlab update(Integer id, TaxSlab slab, Integer userId) {
        TaxSlab existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("TaxSlab not found"));

        User updatingUser = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // Update core fields
        existing.setName(slab.getName());
        existing.setMinAmount(slab.getMinAmount());
        existing.setMaxAmount(slab.getMaxAmount());
        existing.setPreviousLimit(slab.getPreviousLimit());
        existing.setRatePercentage(slab.getRatePercentage());
        existing.setEffectiveFrom(slab.getEffectiveFrom());
        existing.setEffectiveTo(slab.getEffectiveTo());
        existing.setDescription(slab.getDescription());
        existing.setTaxpayerStatus(slab.getTaxpayerStatus());

        // Update Audit field
        existing.setUpdatedBy(updatingUser);

        return repo.save(existing);
    }

    @Override
    public void delete(Integer id) {
        repo.deleteById(id);
    }

    @Override
    public TaxSlab getById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("TaxSlab not found"));
    }

    @Override
    public List<TaxSlab> getAll() {
        return repo.findAll();
    }
}