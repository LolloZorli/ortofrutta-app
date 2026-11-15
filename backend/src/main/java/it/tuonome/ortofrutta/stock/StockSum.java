package it.tuonome.ortofrutta.stock;

import java.math.BigDecimal;

/** Proiezione: giacenza totale per prodotto. */
public interface StockSum {
    Long getProductId();

    BigDecimal getQuantity();
}
