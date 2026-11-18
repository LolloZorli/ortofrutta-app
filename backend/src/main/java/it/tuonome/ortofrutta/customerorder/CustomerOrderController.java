package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/customer-orders")
public class CustomerOrderController {

    private final CustomerOrderService service;

    public CustomerOrderController(CustomerOrderService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    public List<CustomerOrderDtos.Response> mine(Authentication auth) {
        return service.myOrders(auth.getName());
    }

    @PostMapping("/mine")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrderDtos.Response createForCustomer(@Valid @RequestBody CustomerOrderDtos.CustomerRequest request,
                                                        Authentication auth) {
        return service.createForCustomer(auth.getName(), request);
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

    @PostMapping("/{id}/deliver")
    public CustomerOrderDtos.Response deliver(@PathVariable Long id,
                                              @Valid @RequestBody CustomerOrderDtos.PickupRequest request) {
        return service.deliver(id, request.paymentMethodId());
    }

    @PostMapping("/{id}/cancel")
    public CustomerOrderDtos.Response cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
