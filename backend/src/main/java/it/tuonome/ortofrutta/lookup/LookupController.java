package it.tuonome.ortofrutta.lookup;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lettura delle tipologiche attive: serve a tutti gli utenti per compilare i moduli. */
@RestController
@RequestMapping("/api/lookups")
public class LookupController {

    private final LookupService service;

    public LookupController(LookupService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, List<LookupItem>> all() {
        Map<String, List<LookupItem>> out = new LinkedHashMap<>();
        for (LookupType t : LookupType.values()) {
            out.put(t.name(), service.list(t, false));
        }
        return out;
    }
}
