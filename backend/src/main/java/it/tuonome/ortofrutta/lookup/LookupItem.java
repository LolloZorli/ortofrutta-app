package it.tuonome.ortofrutta.lookup;

import java.math.BigDecimal;

/** Un valore di tipologica. Gli ultimi tre campi sono valorizzati solo per le unità di misura e le aliquote IVA. */
public record LookupItem(Long id, String code, String name, int sortOrder, boolean active,
                         String shortLabel, Boolean allowDecimals, BigDecimal rate) {
}
