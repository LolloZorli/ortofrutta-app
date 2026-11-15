package it.tuonome.ortofrutta.sale;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import it.tuonome.ortofrutta.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDtos.Response checkout(@Valid @RequestBody SaleDtos.CheckoutRequest request, Authentication auth) {
        return service.checkout(request, Authz.has(auth, Permission.SALES_PRICE_OVERRIDE));
    }

    @GetMapping
    public PageResponse<SaleDtos.Response> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication auth) {
        boolean seesAll = Authz.has(auth, Permission.REPORTS_VIEW) || Authz.has(auth, Permission.SALES_VOID);
        return service.list(PageResponse.pageable(page, size), !seesAll);
    }

    @GetMapping("/{id}")
    public SaleDtos.Response get(@PathVariable Long id, Authentication auth) {
        boolean seesAll = Authz.has(auth, Permission.REPORTS_VIEW) || Authz.has(auth, Permission.SALES_VOID);
        return service.get(id, !seesAll);
    }

    @PostMapping("/{id}/void")
    public SaleDtos.Response voidSale(@PathVariable Long id, @Valid @RequestBody SaleDtos.VoidRequest request) {
        return service.voidSale(id, request.reason());
    }
}
