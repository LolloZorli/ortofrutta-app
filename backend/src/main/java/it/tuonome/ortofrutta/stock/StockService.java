package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.CurrentUser;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.common.PageResponse;
import it.tuonome.ortofrutta.lookup.LookupItem;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.supplier.Supplier;
import it.tuonome.ortofrutta.supplier.SupplierRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuore del magazzino. Le uscite (vendite e scarti) consumano i lotti col criterio FIFO:
 * prima la merce arrivata prima, così il costo e la freschezza sono tracciati per lotto.
 * Ogni movimento registra chi l'ha eseguito; le vendite registrano anche l'aliquota IVA del momento.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final LotRepository lots;
    private final StockMovementRepository movements;
    private final LookupService lookups;

    public StockService(ProductRepository products, SupplierRepository suppliers,
                        LotRepository lots, StockMovementRepository movements, LookupService lookups) {
        this.products = products;
        this.suppliers = suppliers;
        this.lots = lots;
        this.movements = movements;
        this.lookups = lookups;
    }

    // ---------- letture ----------

    public List<StockDtos.LotResponse> availableLots() {
        return lots.findAllAvailable().stream()
                .map(l -> StockDtos.LotResponse.from(l, lookups.unitLabel(l.getProduct().getUnitId())))
                .toList();
    }

    public PageResponse<StockDtos.MovementResponse> movements(Pageable pageable) {
        return PageResponse.of(movements.findAllByOrderByCreatedAtDescIdDesc(pageable),
                m -> StockDtos.MovementResponse.from(m, lookups.unitLabel(m.getProduct().getUnitId())));
    }

    // ---------- API usata dai controller ----------

    @Transactional
    public StockDtos.LotResponse receive(StockDtos.ReceiptRequest r) {
        Lot lot = receiveLot(r.productId(), r.supplierId(), r.quantity(), r.unitCost(), r.receivedAt(), r.expiresAt());
        return StockDtos.LotResponse.from(lot, lookups.unitLabel(lot.getProduct().getUnitId()));
    }

    @Transactional
    public List<StockDtos.MovementResponse> discard(StockDtos.WasteRequest r) {
        LookupItem reason = lookups.requireActive(LookupType.WASTE_REASON, r.reasonId(), "Motivo di scarto");
        Product p = findProduct(r.productId());
        return discardProduct(r.productId(), r.quantity(), reason.id(), reason.name()).stream()
                .map(m -> StockDtos.MovementResponse.from(m, lookups.unitLabel(p.getUnitId())))
                .toList();
    }

    // ---------- API usata dagli altri servizi (ordini fornitori, cassa) ----------

    @Transactional
    public Lot receiveLot(Long productId, Long supplierId, BigDecimal quantity, BigDecimal unitCost,
                          LocalDate receivedAt, LocalDate expiresAt) {
        Product p = findProduct(productId);
        requireActive(p);
        checkQuantity(p, quantity);
        Supplier s = supplierId == null ? null
                : suppliers.findById(supplierId).orElseThrow(() -> new NotFoundException("Fornitore", supplierId));
        LocalDate date = receivedAt != null ? receivedAt : LocalDate.now();
        if (expiresAt != null && expiresAt.isBefore(date)) {
            throw new BusinessException("La scadenza è precedente alla data di arrivo");
        }
        BigDecimal qty = scale(quantity);
        Lot lot = lots.save(new Lot(p, s, date, expiresAt, qty, unitCost));
        StockMovement m = new StockMovement(p, lot, MovementType.RECEIPT, qty, null, unitCost, null);
        m.tag(null, null, null, CurrentUser.name());
        movements.save(m);
        return lot;
    }

    /** Vendita di un prodotto; saleId collega i movimenti alla vendita di cassa (può essere null). */
    @Transactional
    public List<StockMovement> sellProduct(Long productId, BigDecimal quantity, BigDecimal unitPrice, Long saleId) {
        Product p = findProduct(productId);
        requireActive(p);
        checkQuantity(p, quantity);
        return consume(p, scale(quantity), MovementType.SALE, unitPrice, null, saleId, null);
    }

    @Transactional
    public List<StockMovement> discardProduct(Long productId, BigDecimal quantity, Long reasonId, String reasonName) {
        Product p = findProduct(productId);
        checkQuantity(p, quantity);
        return consume(p, scale(quantity), MovementType.WASTE, null, reasonName, null, reasonId);
    }

    // ---------- logica FIFO ----------

    private List<StockMovement> consume(Product p, BigDecimal quantity, MovementType type, BigDecimal unitPrice,
                                        String reason, Long saleId, Long wasteReasonId) {
        List<Lot> available = lots.findAvailableForUpdate(p.getId());
        BigDecimal total = available.stream()
                .map(Lot::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(quantity) < 0) {
            throw new InsufficientStockException(p.getName(), total, quantity);
        }

        BigDecimal vat = type == MovementType.SALE ? lookups.vatRate(p.getVatRateId()) : null;
        String user = CurrentUser.name();
        List<StockMovement> result = new ArrayList<>();
        BigDecimal left = quantity;
        for (Lot lot : available) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal take = lot.getRemainingQuantity().min(left);
            lot.consume(take);
            left = left.subtract(take);
            StockMovement m = new StockMovement(p, lot, type, take, unitPrice, lot.getUnitCost(), reason);
            m.tag(saleId, wasteReasonId, vat, user);
            result.add(movements.save(m));
        }
        return result;
    }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Prodotto", id));
    }

    private static void requireActive(Product p) {
        if (!p.isActive()) {
            throw new BusinessException("Il prodotto \"" + p.getName() + "\" è disattivato");
        }
    }

    /** Se l'unità di misura non ammette decimali (es. pezzi), la quantità deve essere intera. */
    private void checkQuantity(Product p, BigDecimal quantity) {
        LookupItem unit = lookups.find(LookupType.UNIT, p.getUnitId());
        if (unit != null && Boolean.FALSE.equals(unit.allowDecimals())
                && quantity.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("Per \"" + p.getName() + "\" (" + unit.shortLabel()
                    + ") la quantità deve essere un numero intero");
        }
    }

    private static BigDecimal scale(BigDecimal q) {
        return q.setScale(3, RoundingMode.HALF_UP);
    }
}
