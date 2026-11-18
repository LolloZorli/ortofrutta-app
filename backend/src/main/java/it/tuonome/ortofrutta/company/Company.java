package it.tuonome.ortofrutta.company;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Company() { }

    public Company(String name, String slug) {
        this.name = name.trim();
        this.slug = slug;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
