package it.tuonome.ortofrutta.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 32)
    private String barcode;

    /** Riferimenti alle tipologiche (chiavi esterne nel database): categoria, unità, origine, aliquota IVA. */
    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long unitId;

    private Long originId;

    @Column(nullable = false)
    private Long vatRateId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String name, Long categoryId, Long unitId, BigDecimal salePrice,
                   BigDecimal minStock, Long originId, Long vatRateId) {
        this(name, categoryId, unitId, salePrice, minStock, originId, vatRateId, null);
    }

    public Product(String name, Long categoryId, Long unitId, BigDecimal salePrice,
                   BigDecimal minStock, Long originId, Long vatRateId, String barcode) {
        update(name, categoryId, unitId, salePrice, minStock, originId, vatRateId, barcode);
    }

    public void update(String name, Long categoryId, Long unitId, BigDecimal salePrice,
                       BigDecimal minStock, Long originId, Long vatRateId) {
        update(name, categoryId, unitId, salePrice, minStock, originId, vatRateId, null);
    }

    public void update(String name, Long categoryId, Long unitId, BigDecimal salePrice,
                       BigDecimal minStock, Long originId, Long vatRateId, String barcode) {
        this.name = name.trim();
        this.barcode = barcode == null || barcode.isBlank() ? null : barcode.trim();
        this.categoryId = categoryId;
        this.unitId = unitId;
        this.salePrice = salePrice;
        this.minStock = minStock == null ? BigDecimal.ZERO : minStock;
        this.originId = originId;
        this.vatRateId = vatRateId;
    }

    public void setActive(boolean active) { this.active = active; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBarcode() { return barcode; }
    public Long getCategoryId() { return categoryId; }
    public Long getUnitId() { return unitId; }
    public Long getOriginId() { return originId; }
    public Long getVatRateId() { return vatRateId; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getMinStock() { return minStock; }
    public boolean isActive() { return active; }
}
