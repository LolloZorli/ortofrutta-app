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
    public record Item(Long id, String name, String category, String unit, BigDecimal salePrice,
                       BigDecimal availableQuantity) { }
    private final JdbcClient jdbc;

    public CatalogController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Item> list() {
        return jdbc.sql("""
                select p.id, p.name, c.name as category, u.short_label as unit, p.sale_price,
                       greatest(coalesce(stock.quantity, 0) - coalesce(reserved.quantity, 0), 0) as available_quantity
                from products p join product_categories c on c.id=p.category_id join units u on u.id=p.unit_id
                left join lateral (
                    select sum(l.remaining_quantity) as quantity from lots l
                    where l.product_id = p.id and l.company_id = p.company_id and l.remaining_quantity > 0
                ) stock on true
                left join lateral (
                    select sum(col.quantity) as quantity
                    from customer_order_lines col
                    join customer_orders co on co.id = col.order_id and co.company_id = col.company_id
                    where col.product_id = p.id and col.company_id = p.company_id
                      and co.status in ('NEW', 'READY')
                ) reserved on true
                where p.active and p.company_id = :companyId order by p.name
                """).param("companyId", CompanyContext.requireId()).query((rs, n) -> new Item(rs.getLong("id"), rs.getString("name"),
                rs.getString("category"), rs.getString("unit"), rs.getBigDecimal("sale_price"), rs.getBigDecimal("available_quantity"))).list();
    }
}
