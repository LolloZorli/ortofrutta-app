package it.tuonome.ortofrutta.supplier;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final LookupService lookups;

    public SupplierService(SupplierRepository suppliers, LookupService lookups) {
        this.suppliers = suppliers;
        this.lookups = lookups;
    }

    public List<SupplierDtos.Response> list() {
        return suppliers.findAllByOrderByNameAsc().stream()
                .map(s -> SupplierDtos.Response.from(s, lookups.name(LookupType.SUPPLIER_TYPE, s.getTypeId())))
                .toList();
    }

    @Transactional
    public SupplierDtos.Response create(SupplierDtos.Request r) {
        if (suppliers.existsByNameIgnoreCase(r.name().trim())) {
            throw new BusinessException("Esiste già un fornitore con questo nome");
        }
        Long typeId = requireType(r.typeId());
        Supplier saved = suppliers.save(new Supplier(r.name(), r.phone(), r.email(), r.notes(), typeId));
        return SupplierDtos.Response.from(saved, lookups.name(LookupType.SUPPLIER_TYPE, typeId));
    }

    @Transactional
    public SupplierDtos.Response update(Long id, SupplierDtos.Request r) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Fornitore", id));
        if (suppliers.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new BusinessException("Esiste già un fornitore con questo nome");
        }
        Long typeId = r.typeId();
        if (typeId != null && !typeId.equals(s.getTypeId())) {
            typeId = requireType(typeId);
        }
        s.update(r.name(), r.phone(), r.email(), r.notes(), typeId);
        return SupplierDtos.Response.from(s, lookups.name(LookupType.SUPPLIER_TYPE, typeId));
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

    private Long requireType(Long typeId) {
        if (typeId == null) return null;
        return lookups.requireActive(LookupType.SUPPLIER_TYPE, typeId, "Tipo di fornitore").id();
    }
}
