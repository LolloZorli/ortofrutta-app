package it.tuonome.ortofrutta.supplierorder;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import it.tuonome.ortofrutta.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-orders")
public class SupplierOrderController {

    private final SupplierOrderService service;

    public SupplierOrderController(SupplierOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SupplierOrderDtos.Response> list(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Authentication auth) {
        PageResponse<SupplierOrderDtos.Response> result = service.list(PageResponse.pageable(page, size));
        if (Authz.has(auth, Permission.COSTS_VIEW)) {
            return result;
        }
        return new PageResponse<>(result.content().stream().map(SupplierOrderDtos.Response::withoutCosts).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    public SupplierOrderDtos.Response get(@PathVariable Long id, Authentication auth) {
        return hide(service.get(id), auth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierOrderDtos.Response create(@Valid @RequestBody SupplierOrderDtos.Request request,
                                             Authentication auth) {
        return hide(service.create(request), auth);
    }

    @PostMapping("/{id}/send")
    public SupplierOrderDtos.Response send(@PathVariable Long id, Authentication auth) {
        return hide(service.send(id), auth);
    }

    @PostMapping("/{id}/receive")
    public SupplierOrderDtos.Response receive(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) SupplierOrderDtos.ReceiveRequest request,
                                              Authentication auth) {
        return hide(service.receive(id, request), auth);
    }

    @PostMapping("/{id}/cancel")
    public SupplierOrderDtos.Response cancel(@PathVariable Long id, Authentication auth) {
        return hide(service.cancel(id), auth);
    }

    private static SupplierOrderDtos.Response hide(SupplierOrderDtos.Response r, Authentication auth) {
        return Authz.has(auth, Permission.COSTS_VIEW) ? r : r.withoutCosts();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
