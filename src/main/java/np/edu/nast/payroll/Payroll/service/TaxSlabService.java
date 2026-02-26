package np.edu.nast.payroll.Payroll.service;

import np.edu.nast.payroll.Payroll.entity.TaxSlab;
import java.util.List;

public interface TaxSlabService {

    // Cleaned up: Only keep the methods that accept userId for auditing
    TaxSlab create(TaxSlab slab, Integer userId);

    TaxSlab update(Integer id, TaxSlab slab, Integer userId);

    void delete(Integer id);

    TaxSlab getById(Integer id);

    List<TaxSlab> getAll();
}