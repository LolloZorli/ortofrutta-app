package it.tuonome.ortofrutta.lookup;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class LookupDtos {

    private LookupDtos() {
    }

    /** Il codice interno si genera dal nome e non si modifica: i dati collegati puntano all'id. */
    public record Request(
            @NotBlank @Size(max = 80) String name,
            Integer sortOrder,
            Boolean active,
            @Size(max = 20) String shortLabel,
            Boolean allowDecimals,
            @DecimalMin("0") @DecimalMax("100") BigDecimal rate
    ) {
    }

    public record TypeInfo(String type, String label, boolean hasUnitAttributes, boolean hasRate) {
        static TypeInfo from(LookupType t) {
            return new TypeInfo(t.name(), t.label(), t.hasUnitAttributes(), t.hasRate());
        }
    }
}
