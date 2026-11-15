package it.tuonome.ortofrutta.sale;

import java.math.BigDecimal;

/** Una riga di vendita con il prezzo già deciso. */
public record SaleItem(Long productId, BigDecimal quantity, BigDecimal unitPrice) {
}
