package com.acme.export.amount;

import java.math.BigDecimal;

public class AmountParser {

    /** Converte o texto de um valor recebido de um importador num valor canonico. */
    public BigDecimal parse(String text) {
        return new BigDecimal(text.trim());
    }
}
