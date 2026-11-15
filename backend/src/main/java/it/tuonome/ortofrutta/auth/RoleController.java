package it.tuonome.ortofrutta.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/permissions")
    public List<RoleDtos.PermissionInfo> permissions() {
        return service.permissionCatalog();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDtos.Response create(@Valid @RequestBody RoleDtos.Request request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RoleDtos.Response update(@PathVariable Long id, @Valid @RequestBody RoleDtos.Request request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
