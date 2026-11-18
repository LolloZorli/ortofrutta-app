package it.tuonome.ortofrutta.supplierorder;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.supplier.Supplier;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "supplier_orders")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class SupplierOrder extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SupplierOrderStatus status = SupplierOrderStatus.DRAFT;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant sentAt;

    private Instant receivedAt;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SupplierOrderLine> lines = new ArrayList<>();

    protected SupplierOrder() {
    }

    public SupplierOrder(Supplier supplier, String notes) {
        this.supplier = supplier;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void addLine(Product product, BigDecimal quantity, BigDecimal unitCost) {
        lines.add(new SupplierOrderLine(this, product, quantity, unitCost));
    }

    public void markSent() {
        this.status = SupplierOrderStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markReceived() {
        this.status = SupplierOrderStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = SupplierOrderStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public SupplierOrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getNotes() { return notes; }
    public List<SupplierOrderLine> getLines() { return lines; }
}
