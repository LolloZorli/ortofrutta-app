package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.lookup.LookupItem;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Test unitario della logica FIFO: nessun database, repository simulati. */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock ProductRepository products;
    @Mock SupplierRepository suppliers;
    @Mock LotRepository lots;
    @Mock StockMovementRepository movements;
    @Mock LookupService lookups;

    StockService service;
    Product apples;

    @BeforeEach
    void setUp() {
        service = new StockService(products, suppliers, lots, movements, lookups);
        apples = new Product("Mele", 1L, 1L, new BigDecimal("2.00"), BigDecimal.ZERO, null, 1L);
        ReflectionTestUtils.setField(apples, "id", 1L);
        when(products.findById(1L)).thenReturn(Optional.of(apples));
        lenient().when(movements.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Lot lot(String qty, String cost, int daysAgo) {
        return new Lot(apples, null, LocalDate.now().minusDays(daysAgo), null,
                new BigDecimal(qty), new BigDecimal(cost));
    }

    @Test
    void saleConsumesOldestLotFirst() {
        Lot old = lot("5", "1.00", 5);
        Lot recent = lot("10", "1.50", 1);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(old, recent));

        List<StockMovement> result = service.sellProduct(1L, new BigDecimal("7"), new BigDecimal("2.00"), 99L);

        assertThat(result).hasSize(2);
        assertThat(old.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(recent.getRemainingQuantity()).isEqualByComparingTo("8");
        // ogni movimento porta il costo del proprio lotto: serve a calcolare il margine reale
        assertThat(result.get(0).getUnitCost()).isEqualByComparingTo("1.00");
        assertThat(result.get(1).getUnitCost()).isEqualByComparingTo("1.50");
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("5");
        assertThat(result.get(1).getQuantity()).isEqualByComparingTo("2");
        // e sono collegati alla vendita
        assertThat(result).allSatisfy(m -> assertThat(m.getSaleId()).isEqualTo(99L));
    }

    @Test
    void saleWithinSingleLotCreatesOneMovement() {
        Lot only = lot("10", "1.00", 2);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(only));

        List<StockMovement> result = service.sellProduct(1L, new BigDecimal("3.5"), new BigDecimal("2.00"), null);

        assertThat(result).hasSize(1);
        assertThat(only.getRemainingQuantity()).isEqualByComparingTo("6.5");
        assertThat(result.get(0).getType()).isEqualTo(MovementType.SALE);
    }

    @Test
    void saleRejectedWhenStockIsInsufficient() {
        Lot only = lot("3", "1.00", 2);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(only));

        assertThatThrownBy(() -> service.sellProduct(1L, new BigDecimal("4"), new BigDecimal("2.00"), null))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Mele");
        assertThat(only.getRemainingQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void saleRecordsTheVatRateOfTheMoment() {
        Lot only = lot("10", "1.00", 2);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(only));
        when(lookups.vatRate(1L)).thenReturn(new BigDecimal("4.00"));

        List<StockMovement> result = service.sellProduct(1L, BigDecimal.ONE, new BigDecimal("2.00"), null);

        assertThat(result.get(0).getVatRate()).isEqualByComparingTo("4.00");
    }

    @Test
    void wasteIsRecordedWithReasonAndCost() {
        Lot only = lot("4", "1.20", 3);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(only));

        List<StockMovement> result = service.discardProduct(1L, BigDecimal.ONE, 7L, "Troppo mature");

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.getType()).isEqualTo(MovementType.WASTE);
            assertThat(m.getReason()).isEqualTo("Troppo mature");
            assertThat(m.getWasteReasonId()).isEqualTo(7L);
            assertThat(m.getUnitCost()).isEqualByComparingTo("1.20");
        });
    }

    @Test
    void cannotSellInactiveProduct() {
        apples.setActive(false);

        assertThatThrownBy(() -> service.sellProduct(1L, BigDecimal.ONE, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disattivato");
    }

    @Test
    void receiptRejectsExpiryBeforeArrival() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.receiveLot(1L, null, BigDecimal.TEN, BigDecimal.ONE,
                today, today.minusDays(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unitWithoutDecimalsRejectsFractionalQuantities() {
        when(lookups.find(LookupType.UNIT, 1L)).thenReturn(
                new LookupItem(1L, "PIECE", "Pezzo", 2, true, "pz", false, null));

        assertThatThrownBy(() -> service.sellProduct(1L, new BigDecimal("1.5"), BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("intero");
    }

    @Test
    void unitWithoutDecimalsAcceptsWholeQuantities() {
        when(lookups.find(LookupType.UNIT, 1L)).thenReturn(
                new LookupItem(1L, "PIECE", "Pezzo", 2, true, "pz", false, null));
        Lot only = lot("10", "1.00", 1);
        when(lots.findAvailableForUpdate(1L)).thenReturn(List.of(only));

        // 2 e 2.000 sono entrambi interi
        assertThat(service.sellProduct(1L, new BigDecimal("2.000"), BigDecimal.ONE, null)).hasSize(1);
        assertThat(only.getRemainingQuantity()).isEqualByComparingTo("8");
    }
}
