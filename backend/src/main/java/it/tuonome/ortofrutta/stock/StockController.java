package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.auth.Authz;
import it.tuonome.ortofrutta.auth.Permission;
import it.tuonome.ortofrutta.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping("/lots")
    public List<StockDtos.LotResponse> lots(Authentication auth) {
        List<StockDtos.LotResponse> lots = service.availableLots();
        return Authz.has(auth, Permission.COSTS_VIEW)
                ? lots : lots.stream().map(StockDtos.LotResponse::withoutCost).toList();
    }

    @GetMapping("/movements")
    public PageResponse<StockDtos.MovementResponse> movements(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "30") int size,
                                                              Authentication auth) {
        PageResponse<StockDtos.MovementResponse> result = service.movements(PageResponse.pageable(page, size));
        if (Authz.has(auth, Permission.COSTS_VIEW)) {
            return result;
        }
        return new PageResponse<>(result.content().stream().map(StockDtos.MovementResponse::withoutCost).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public StockDtos.LotResponse receive(@Valid @RequestBody StockDtos.ReceiptRequest request) {
        return service.receive(request);
    }

    @PostMapping("/waste")
    public List<StockDtos.MovementResponse> discard(@Valid @RequestBody StockDtos.WasteRequest request) {
        return service.discard(request);
    }

    @PostMapping("/count")
    public java.math.BigDecimal inventoryCount(@Valid @RequestBody StockDtos.CountRequest request) {
        return service.inventoryCount(request.productId(), request.countedQuantity());
    }
}
