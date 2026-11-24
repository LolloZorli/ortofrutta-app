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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** La visibilità della dashboard è riservata ai ruoli operativi; gli importi dipendono dai permessi dell'utente. */
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
                i.stock(), i.minStock(), i.suggestedQuantity(), null, i.averageDailySales(), i.daysOfStock())).toList();
    }

    @GetMapping("/products")
    public List<ReportDtos.ProductReport> products(@RequestParam(defaultValue = "30") int days) {
        return service.productReport(days);
    }

    @GetMapping(value = "/products/export", produces = "text/csv")
    public ResponseEntity<String> exportProducts(@RequestParam(defaultValue = "30") int days) {
        StringBuilder csv = new StringBuilder(
                "Prodotto;Unita;Quantita venduta;Incasso;Costo;Margine;Margine %;Quantita scarti;Costo scarti;Scarto %\n");
        for (ReportDtos.ProductReport row : service.productReport(days)) {
            csv.append(csv(row.productName())).append(';').append(csv(row.unit())).append(';')
                    .append(row.soldQuantity()).append(';').append(row.revenue()).append(';')
                    .append(row.cost()).append(';').append(row.margin()).append(';')
                    .append(row.marginPercent()).append(';').append(row.wasteQuantity()).append(';')
                    .append(row.wasteCost()).append(';').append(row.wastePercent()).append('\n');
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "report-prodotti-" + days + "-giorni.csv");
        return ResponseEntity.ok().headers(headers).body('\ufeff' + csv.toString());
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.indexOf(';') >= 0 || escaped.indexOf('"') >= 0 || escaped.indexOf('\n') >= 0
                ? "\"" + escaped + "\"" : escaped;
    }

    @GetMapping("/corrispettivi")
    public ReportDtos.Corrispettivi corrispettivi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.corrispettivi(date != null ? date : LocalDate.now(ZoneId.of("Europe/Rome")));
    }

    @GetMapping("/cash-closing")
    public ReportDtos.CashClosing cashClosing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.cashClosing(date);
    }

    @PostMapping("/cash-closing")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDtos.CashClosing saveCashClosing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @Valid @RequestBody ReportDtos.CashClosingRequest request) {
        return service.saveCashClosing(date, request);
    }
}
