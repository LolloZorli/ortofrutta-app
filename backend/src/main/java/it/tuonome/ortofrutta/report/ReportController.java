package it.tuonome.ortofrutta.report;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** La dashboard è per tutti, ma gli importi dipendono dai permessi dell'utente. */
    @GetMapping("/dashboard")
    public ReportDtos.Dashboard dashboard(Authentication auth) {
        return service.dashboard().restrict(
                Authz.has(auth, Permission.REPORTS_VIEW), Authz.has(auth, Permission.COSTS_VIEW));
    }

    @GetMapping("/reorder-suggestions")
    public List<ReportDtos.LowStockItem> reorderSuggestions(Authentication auth) {
        List<ReportDtos.LowStockItem> items = service.lowStock();
        if (Authz.has(auth, Permission.COSTS_VIEW)) {
            return items;
        }
        return items.stream().map(i -> new ReportDtos.LowStockItem(i.productId(), i.productName(), i.unit(),
                i.stock(), i.minStock(), i.suggestedQuantity(), null)).toList();
    }

    @GetMapping("/products")
    public List<ReportDtos.ProductReport> products(@RequestParam(defaultValue = "30") int days) {
        return service.productReport(days);
    }

    @GetMapping("/corrispettivi")
    public ReportDtos.Corrispettivi corrispettivi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.corrispettivi(date != null ? date : LocalDate.now(ZoneId.of("Europe/Rome")));
    }
}
