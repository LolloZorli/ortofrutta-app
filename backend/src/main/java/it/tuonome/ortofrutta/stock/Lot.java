package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.supplier.Supplier;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una partita di merce arrivata: ha un costo, una data di arrivo e (facoltativa) di scadenza. */
@Entity
@Table(name = "lots")
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false)
    private LocalDate receivedAt;

    private LocalDate expiresAt;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal initialQuantity;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal remainingQuantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Lot() {
    }

    public Lot(Product product, Supplier supplier, LocalDate receivedAt, LocalDate expiresAt,
               BigDecimal quantity, BigDecimal unitCost) {
        this.product = product;
        this.supplier = supplier;
        this.receivedAt = receivedAt;
        this.expiresAt = expiresAt;
        this.initialQuantity = quantity;
        this.remainingQuantity = quantity;
        this.unitCost = unitCost;
    }

    /** Preleva una quantità dal lotto. */
    public void consume(BigDecimal quantity) {
        this.remainingQuantity = this.remainingQuantity.subtract(quantity);
    }

    /** Rimette in lotto una quantità (storno di una vendita). Non può superare la quantità iniziale. */
    public void restore(BigDecimal quantity) {
        BigDecimal updated = this.remainingQuantity.add(quantity);
        if (updated.compareTo(this.initialQuantity) > 0) {
            throw new IllegalStateException("Il lotto " + id + " supererebbe la quantità iniziale");
        }
        this.remainingQuantity = updated;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Supplier getSupplier() { return supplier; }
    public LocalDate getReceivedAt() { return receivedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public BigDecimal getInitialQuantity() { return initialQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public Instant getCreatedAt() { return createdAt; }
}
