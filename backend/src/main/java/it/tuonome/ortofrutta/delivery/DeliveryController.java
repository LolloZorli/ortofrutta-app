package it.tuonome.ortofrutta.delivery;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-zones")
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) { this.service = service; }

    @GetMapping("/options")
    public DeliveryDtos.OptionsResponse options(@RequestParam(required = false) String city,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.options(city, date);
    }

    @GetMapping
    public List<DeliveryDtos.ZoneResponse> list() { return service.listZones(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryDtos.ZoneResponse create(@Valid @RequestBody DeliveryDtos.ZoneRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DeliveryDtos.ZoneResponse update(@PathVariable long id, @Valid @RequestBody DeliveryDtos.ZoneRequest request) {
        return service.update(id, request);
    }
}
