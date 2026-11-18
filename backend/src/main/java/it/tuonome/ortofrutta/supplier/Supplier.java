package it.tuonome.ortofrutta.supplier;

import it.tuonome.ortofrutta.company.CompanyScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "suppliers")
@SQLRestriction(CompanyScopedEntity.RESTRICTION)
public class Supplier extends CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 500)
    private String notes;

    protected Supplier() {
    }

    public Supplier(String name, String phone, String email, String notes) {
        update(name, phone, email, notes);
    }

    public void update(String name, String phone, String email, String notes) {
        this.name = name.trim();
        this.phone = blankToNull(phone);
        this.email = blankToNull(email);
        this.notes = blankToNull(notes);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getNotes() { return notes; }
}
