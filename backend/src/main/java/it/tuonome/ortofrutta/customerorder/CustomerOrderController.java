package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-orders")
public class CustomerOrderController {

    private final CustomerOrderService service;

    public CustomerOrderController(CustomerOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerOrderDtos.Response> list(@RequestParam(defaultValue = "true") boolean onlyOpen) {
        return service.list(onlyOpen);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrderDtos.Response create(@Valid @RequestBody CustomerOrderDtos.Request request,
                                             Authentication auth) {
        return service.create(request, Authz.has(auth, Permission.SALES_PRICE_OVERRIDE));
    }

    @PostMapping("/{id}/ready")
    public CustomerOrderDtos.Response ready(@PathVariable Long id) {
        return service.markReady(id);
    }

    @PostMapping("/{id}/pickup")
    public CustomerOrderDtos.Response pickUp(@PathVariable Long id,
                                             @Valid @RequestBody CustomerOrderDtos.PickupRequest request) {
        return service.pickUp(id, request.paymentMethodId());
    }

    @PostMapping("/{id}/cancel")
    public CustomerOrderDtos.Response cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
