package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Storico: ogni carico, vendita o scarto. Una vendita su più lotti genera più movimenti. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MovementType type;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(length = 120)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Vendita a cui appartiene il movimento (solo per le vendite fatte dalla cassa). */
    private Long saleId;

    /** Utente che ha eseguito l'operazione. */
    @Column(length = 50)
    private String createdBy;

    /** Vero se la vendita è stata stornata: il movimento resta nello storico ma non conta nei report. */
    @Column(nullable = false)
    private boolean voided;

    private Long wasteReasonId;

    /** Aliquota IVA applicata al momento della vendita (resta invariata anche se poi l'aliquota cambia). */
    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate;

    protected StockMovement() {
    }

    public StockMovement(Product product, Lot lot, MovementType type, BigDecimal quantity,
                         BigDecimal unitPrice, BigDecimal unitCost, String reason) {
        this.product = product;
        this.lot = lot;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.reason = reason;
    }

    public void tag(Long saleId, Long wasteReasonId, BigDecimal vatRate, String createdBy) {
        this.saleId = saleId;
        this.wasteReasonId = wasteReasonId;
        this.vatRate = vatRate;
        this.createdBy = createdBy;
    }

    public void markVoided() {
        this.voided = true;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Lot getLot() { return lot; }
    public MovementType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getSaleId() { return saleId; }
    public String getCreatedBy() { return createdBy; }
    public boolean isVoided() { return voided; }
    public Long getWasteReasonId() { return wasteReasonId; }
    public BigDecimal getVatRate() { return vatRate; }
}
