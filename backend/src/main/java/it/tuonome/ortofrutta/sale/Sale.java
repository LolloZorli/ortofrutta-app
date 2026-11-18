package it.tuonome.ortofrutta.sale;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

/** Una vendita di cassa: può contenere più prodotti, ha un metodo di pagamento e si può stornare. */
@Entity
@Table(name = "sales")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class Sale extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 50)
    private String createdBy;

    private Long paymentMethodId;

    private Long customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SaleStatus status = SaleStatus.COMPLETED;

    private Instant voidedAt;

    @Column(length = 50)
    private String voidedBy;

    @Column(length = 200)
    private String voidReason;

    protected Sale() {
    }

    public Sale(String createdBy, Long paymentMethodId, Long customerId) {
        this.createdBy = createdBy;
        this.paymentMethodId = paymentMethodId;
        this.customerId = customerId;
    }

    public void setTotal(BigDecimal total) { this.total = total; }

    public void markVoided(String by, String reason) {
        this.status = SaleStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.voidedBy = by;
        this.voidReason = reason;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getTotal() { return total; }
    public SaleStatus getStatus() { return status; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidedBy() { return voidedBy; }
    public String getVoidReason() { return voidReason; }
}
