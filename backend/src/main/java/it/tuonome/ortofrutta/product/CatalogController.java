package it.tuonome.ortofrutta.product;

import it.tuonome.ortofrutta.company.CompanyContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    public record Item(Long id, String name, String category, String unit, BigDecimal salePrice) { }
    private final JdbcClient jdbc;

    public CatalogController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Item> list() {
        return jdbc.sql("""
                select p.id, p.name, c.name as category, u.short_label as unit, p.sale_price
                from products p join product_categories c on c.id=p.category_id join units u on u.id=p.unit_id
                where p.active and p.company_id = :companyId order by p.name
                """).param("companyId", CompanyContext.requireId()).query((rs, n) -> new Item(rs.getLong("id"), rs.getString("name"),
                rs.getString("category"), rs.getString("unit"), rs.getBigDecimal("sale_price"))).list();
    }
}
