package it.tuonome.ortofrutta.lookup;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/** Gestione delle tipologiche: permesso LOOKUPS_MANAGE, riservato all'amministratore. */
@RestController
@RequestMapping("/api/admin/lookups")
public class LookupAdminController {

    private final LookupService service;

    public LookupAdminController(LookupService service) {
        this.service = service;
    }

    @GetMapping
    public List<LookupDtos.TypeInfo> types() {
        return Arrays.stream(LookupType.values()).map(LookupDtos.TypeInfo::from).toList();
    }

    @GetMapping("/{type}")
    public List<LookupItem> list(@PathVariable LookupType type) {
        return service.listFresh(type);
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public LookupItem create(@PathVariable LookupType type, @Valid @RequestBody LookupDtos.Request request) {
        return service.create(type, request);
    }

    @PutMapping("/{type}/{id}")
    public LookupItem update(@PathVariable LookupType type, @PathVariable Long id,
                             @Valid @RequestBody LookupDtos.Request request) {
        return service.update(type, id, request);
    }
}
