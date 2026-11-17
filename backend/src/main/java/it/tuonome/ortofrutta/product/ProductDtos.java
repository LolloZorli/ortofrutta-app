package it.tuonome.ortofrutta.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** categoria, unità, origine e aliquota IVA sono id di tipologiche (si scelgono da elenchi gestiti dall'admin). */
    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotNull Long categoryId,
            @NotNull Long unitId,
            @NotNull @DecimalMin("0.00") BigDecimal salePrice,
            @DecimalMin("0") BigDecimal minStock,
            Long originId,
            @NotNull Long vatRateId,
            Boolean active,
            @Size(max = 32) String barcode
    ) {
        public Request(String name, Long categoryId, Long unitId, BigDecimal salePrice,
                       BigDecimal minStock, Long originId, Long vatRateId, Boolean active) {
            this(name, categoryId, unitId, salePrice, minStock, originId, vatRateId, active, null);
        }
    }

    /** "unit" è l'etichetta breve (kg, pz...); allowDecimals dice se si possono vendere quantità frazionarie. */
    public record Response(
            Long id, String name,
            Long categoryId, String category,
            Long unitId, String unit, boolean allowDecimals,
            BigDecimal salePrice, BigDecimal minStock,
            Long originId, String origin,
            Long vatRateId, BigDecimal vatRate,
            boolean active, BigDecimal stock, boolean lowStock, String barcode
    ) {
    }
}
