package it.tuonome.ortofrutta.customer;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "customers")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class Customer extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String notes;

    /** Tipo di cliente (tipologica: privato, ristorante, azienda...). Facoltativo. */
    private Long typeId;

    protected Customer() {
    }

    public Customer(String name, String phone, String notes, Long typeId) {
        update(name, phone, notes, typeId);
    }

    public void update(String name, String phone, String notes, Long typeId) {
        this.typeId = typeId;
        this.name = name.trim();
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getNotes() { return notes; }
    public Long getTypeId() { return typeId; }
}
