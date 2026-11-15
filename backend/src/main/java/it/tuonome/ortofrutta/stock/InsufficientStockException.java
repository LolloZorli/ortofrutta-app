package it.tuonome.ortofrutta.stock;

import it.tuonome.ortofrutta.common.BusinessException;

import java.math.BigDecimal;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String productName, BigDecimal available, BigDecimal requested) {
        super("Giacenza insufficiente per " + productName + ": disponibili "
                + available.stripTrailingZeros().toPlainString() + ", richiesti "
                + requested.stripTrailingZeros().toPlainString());
    }
}
