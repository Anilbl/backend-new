package np.edu.nast.payroll.Payroll.controller;

import np.edu.nast.payroll.Payroll.entity.TaxSlab;
import np.edu.nast.payroll.Payroll.service.TaxSlabService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tax-slabs")
@CrossOrigin(origins = "http://localhost:5173")
public class TaxSlabController {

    private final TaxSlabService service;

    public TaxSlabController(TaxSlabService service) {
        this.service = service;
    }

    // UPDATED: Accepts userId as a Query Parameter from the frontend
    @PostMapping
    public TaxSlab create(@RequestBody TaxSlab slab, @RequestParam Integer userId) {
        return service.create(slab, userId);
    }

    // UPDATED: Accepts userId as a Query Parameter for tracking the editor
    @PutMapping("/{id}")
    public TaxSlab update(
            @PathVariable Integer id,
            @RequestBody TaxSlab slab,
            @RequestParam Integer userId) {
        return service.update(id, slab, userId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }

    @GetMapping("/{id}")
    public TaxSlab getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @GetMapping
    public List<TaxSlab> getAll() {
        return service.getAll();
    }
}