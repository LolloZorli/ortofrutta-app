package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import it.tuonome.ortofrutta.product.Product;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_lines")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class CustomerOrderLine extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected CustomerOrderLine() {
    }

    CustomerOrderLine(CustomerOrder order, Product product, BigDecimal quantity, BigDecimal unitPrice) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
