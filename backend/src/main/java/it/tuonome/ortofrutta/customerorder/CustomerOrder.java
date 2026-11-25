package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import it.tuonome.ortofrutta.customer.Customer;
import it.tuonome.ortofrutta.product.Product;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class CustomerOrder extends CompanyScopedEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 10)
    private FulfillmentType fulfillmentType = FulfillmentType.PICKUP;

    @Column(name = "delivery_address", length = 250)
    private String deliveryAddress;

    @Column(name = "delivery_city", length = 120)
    private String deliveryCity;

    @Column(name = "delivery_postal_code", length = 20)
    private String deliveryPostalCode;

    @Column(name = "delivery_phone", length = 30)
    private String deliveryPhone;

    @Column(name = "delivery_notes", length = 500)
    private String deliveryNotes;

    @Column(name = "delivery_zone_id")
    private Long deliveryZoneId;

    @Column(name = "delivery_slot_id")
    private Long deliverySlotId;

    @Column(name = "delivery_slot", length = 80)
    private String deliverySlot;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CustomerOrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, LocalDate pickupDate, String notes) {
        this(customer, pickupDate, notes, FulfillmentType.PICKUP, null, null, null, null, null);
    }

    public CustomerOrder(Customer customer, LocalDate pickupDate, String notes, FulfillmentType fulfillmentType,
                         String deliveryAddress, String deliveryCity, String deliveryPostalCode,
                         String deliveryPhone, String deliveryNotes) {
        this.customer = customer;
        this.pickupDate = pickupDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.fulfillmentType = fulfillmentType == null ? FulfillmentType.PICKUP : fulfillmentType;
        this.deliveryAddress = deliveryAddress;
        this.deliveryCity = clean(deliveryCity);
        this.deliveryPostalCode = clean(deliveryPostalCode);
        this.deliveryPhone = clean(deliveryPhone);
        this.deliveryNotes = clean(deliveryNotes);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public void addLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        lines.add(new CustomerOrderLine(this, product, quantity, unitPrice));
    }

    public void setStatus(CustomerOrderStatus status) { this.status = status; }
    public void setDeliveryTerms(Long zoneId, Long slotId, String slot, BigDecimal fee) {
        this.deliveryZoneId = zoneId;
        this.deliverySlotId = slotId;
        this.deliverySlot = slot;
        this.deliveryFee = fee;
    }
    public boolean isDelivery() { return fulfillmentType == FulfillmentType.DELIVERY; }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public CustomerOrderStatus getStatus() { return status; }
    public LocalDate getPickupDate() { return pickupDate; }
    public String getNotes() { return notes; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getDeliveryCity() { return deliveryCity; }
    public String getDeliveryPostalCode() { return deliveryPostalCode; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public Long getDeliveryZoneId() { return deliveryZoneId; }
    public Long getDeliverySlotId() { return deliverySlotId; }
    public String getDeliverySlot() { return deliverySlot; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CustomerOrderLine> getLines() { return lines; }
}
