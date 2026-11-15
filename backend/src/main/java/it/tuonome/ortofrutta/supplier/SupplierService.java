package it.tuonome.ortofrutta.supplier;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    public List<SupplierDtos.Response> list() {
        return suppliers.findAllByOrderByNameAsc().stream().map(SupplierDtos.Response::from).toList();
    }

    @Transactional
    public SupplierDtos.Response create(SupplierDtos.Request r) {
        if (suppliers.existsByNameIgnoreCase(r.name().trim())) {
            throw new BusinessException("Esiste già un fornitore con questo nome");
        }
        return SupplierDtos.Response.from(
                suppliers.save(new Supplier(r.name(), r.phone(), r.email(), r.notes())));
    }

    @Transactional
    public SupplierDtos.Response update(Long id, SupplierDtos.Request r) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Fornitore", id));
        if (suppliers.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new BusinessException("Esiste già un fornitore con questo nome");
        }
        s.update(r.name(), r.phone(), r.email(), r.notes());
        return SupplierDtos.Response.from(s);
    }

    /** Se il fornitore è collegato a lotti o ordini, il vincolo del database lo impedisce (HTTP 409). */
    @Transactional
    public void delete(Long id) {
        if (!suppliers.existsById(id)) {
            throw new NotFoundException("Fornitore", id);
        }
        suppliers.deleteById(id);
        suppliers.flush();
    }
}
