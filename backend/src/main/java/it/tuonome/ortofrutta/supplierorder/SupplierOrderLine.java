package it.tuonome.ortofrutta.supplierorder;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import it.tuonome.ortofrutta.product.Product;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_order_lines")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class SupplierOrderLine extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private SupplierOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost;

    protected SupplierOrderLine() {
    }

    SupplierOrderLine(SupplierOrder order, Product product, BigDecimal quantity, BigDecimal unitCost) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
