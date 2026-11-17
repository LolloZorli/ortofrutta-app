package it.tuonome.ortofrutta.product;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.lookup.LookupItem;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.stock.LotRepository;
import it.tuonome.ortofrutta.stock.StockSum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final LotRepository lots;
    private final LookupService lookups;
    private final AuditService audit;

    public ProductService(ProductRepository products, LotRepository lots, LookupService lookups, AuditService audit) {
        this.products = products;
        this.lots = lots;
        this.lookups = lookups;
        this.audit = audit;
    }

    public List<ProductDtos.Response> list(boolean includeInactive) {
        Map<Long, BigDecimal> stock = lots.sumByProduct().stream()
                .collect(Collectors.toMap(StockSum::getProductId, StockSum::getQuantity));
        List<Product> list = includeInactive
                ? products.findAllByOrderByNameAsc()
                : products.findByActiveTrueOrderByNameAsc();
        return list.stream()
                .map(p -> toResponse(p, stock.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList();
    }

    public ProductDtos.Response get(Long id) {
        return toResponse(find(id), lots.stockOf(id));
    }

    @Transactional
    public ProductDtos.Response create(ProductDtos.Request r) {
        if (products.existsByNameIgnoreCase(r.name().trim())) {
            throw new BusinessException("Esiste già un prodotto con questo nome");
        }
        String barcode = normalizeBarcode(r.barcode());
        validateBarcode(barcode, null);
        validateLookups(r, null);
        Product p = products.save(new Product(r.name(), r.categoryId(), r.unitId(), r.salePrice(),
                r.minStock(), r.originId(), r.vatRateId(), barcode));
        if (r.active() != null) {
            p.setActive(r.active());
        }
        audit.record("PRODUCT_CREATE", "PRODUCT", p.getId(), p.getName() + " a " + p.getSalePrice());
        return toResponse(p, BigDecimal.ZERO);
    }

    @Transactional
    public ProductDtos.Response update(Long id, ProductDtos.Request r) {
        Product p = find(id);
        if (products.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new BusinessException("Esiste già un prodotto con questo nome");
        }
        String barcode = normalizeBarcode(r.barcode());
        validateBarcode(barcode, id);
        validateLookups(r, p);
        BigDecimal oldPrice = p.getSalePrice();
        String oldBarcode = p.getBarcode();
        p.update(r.name(), r.categoryId(), r.unitId(), r.salePrice(), r.minStock(), r.originId(), r.vatRateId(), barcode);
        if (r.active() != null) {
            p.setActive(r.active());
        }
        if (oldPrice.compareTo(p.getSalePrice()) != 0) {
            audit.record("PRICE_CHANGE", "PRODUCT", id, p.getName() + ": " + oldPrice + " → " + p.getSalePrice());
        } else if (!java.util.Objects.equals(oldBarcode, barcode)) {
            audit.record("PRODUCT_UPDATE", "PRODUCT", id, p.getName() + ": codice a barre " +
                    (oldBarcode == null ? "assente" : oldBarcode) + " → " + (barcode == null ? "rimosso" : barcode));
        } else {
            audit.record("PRODUCT_UPDATE", "PRODUCT", id, p.getName());
        }
        return toResponse(p, lots.stockOf(id));
    }

    /** I prodotti non si cancellano (hanno storico): si disattivano. */
    @Transactional
    public void deactivate(Long id) {
        Product p = find(id);
        p.setActive(false);
        audit.record("PRODUCT_DEACTIVATE", "PRODUCT", id, p.getName());
    }

    // ---------------------------------- interni ----------------------------------

    /** I valori scelti devono esistere ed essere attivi; si accetta però il valore già assegnato al prodotto, anche se poi disattivato. */
    private void validateLookups(ProductDtos.Request r, Product current) {
        check(LookupType.CATEGORY, r.categoryId(), current == null ? null : current.getCategoryId(), "Categoria");
        check(LookupType.UNIT, r.unitId(), current == null ? null : current.getUnitId(), "Unità di misura");
        check(LookupType.VAT_RATE, r.vatRateId(), current == null ? null : current.getVatRateId(), "Aliquota IVA");
        if (r.originId() != null) {
            check(LookupType.ORIGIN, r.originId(), current == null ? null : current.getOriginId(), "Origine");
        }
    }

    private void check(LookupType type, Long id, Long currentId, String what) {
        if (id.equals(currentId)) {
            if (lookups.find(type, id) == null) {
                throw new BusinessException(what + " non valido");
            }
            return;
        }
        lookups.requireActive(type, id, what);
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return null;
        return barcode.trim();
    }

    private void validateBarcode(String barcode, Long currentId) {
        if (barcode == null) return;
        boolean duplicate = currentId == null
                ? products.existsByBarcodeIgnoreCase(barcode)
                : products.existsByBarcodeIgnoreCaseAndIdNot(barcode, currentId);
        if (duplicate) throw new BusinessException("Questo codice a barre è già assegnato a un altro prodotto");
    }

    private ProductDtos.Response toResponse(Product p, BigDecimal stock) {
        LookupItem unit = lookups.find(LookupType.UNIT, p.getUnitId());
        boolean low = p.getMinStock().signum() > 0 && stock.compareTo(p.getMinStock()) < 0;
        return new ProductDtos.Response(p.getId(), p.getName(),
                p.getCategoryId(), lookups.name(LookupType.CATEGORY, p.getCategoryId()),
                p.getUnitId(), unit == null ? "" : unit.shortLabel(),
                unit != null && Boolean.TRUE.equals(unit.allowDecimals()),
                p.getSalePrice(), p.getMinStock(),
                p.getOriginId(), lookups.name(LookupType.ORIGIN, p.getOriginId()),
                p.getVatRateId(), lookups.vatRate(p.getVatRateId()),
                p.isActive(), stock, low, p.getBarcode());
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Prodotto", id));
    }
}
