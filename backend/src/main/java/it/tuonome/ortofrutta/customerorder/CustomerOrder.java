package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.customer.Customer;
import it.tuonome.ortofrutta.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CustomerOrderStatus status = CustomerOrderStatus.NEW;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CustomerOrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, LocalDate pickupDate, String notes) {
        this.customer = customer;
        this.pickupDate = pickupDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void addLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        lines.add(new CustomerOrderLine(this, product, quantity, unitPrice));
    }

    public void setStatus(CustomerOrderStatus status) { this.status = status; }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public CustomerOrderStatus getStatus() { return status; }
    public LocalDate getPickupDate() { return pickupDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CustomerOrderLine> getLines() { return lines; }
}
