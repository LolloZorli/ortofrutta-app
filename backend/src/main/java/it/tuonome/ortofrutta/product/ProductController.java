package it.tuonome.ortofrutta.product;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDtos.Response> list(@RequestParam(defaultValue = "false") boolean includeInactive,
                                           Authentication auth) {
        // i prodotti disattivati li vede solo chi può gestire il listino
        return service.list(includeInactive && Authz.has(auth, Permission.PRODUCTS_WRITE));
    }

    @GetMapping("/{id}")
    public ProductDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDtos.Response create(@Valid @RequestBody ProductDtos.Request request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductDtos.Response update(@PathVariable Long id, @Valid @RequestBody ProductDtos.Request request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
