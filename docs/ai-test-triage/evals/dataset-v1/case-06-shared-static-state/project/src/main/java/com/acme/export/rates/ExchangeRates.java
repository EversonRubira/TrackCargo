package com.acme.export.rates;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Cotacoes para EUR, partilhadas por toda a JVM. */
public final class ExchangeRates {

    private static final Map<String, BigDecimal> RATES = new HashMap<>(Map.of(
            "USD", new BigDecimal("0.90"),
            "BRL", new BigDecimal("0.17")));

    private ExchangeRates() {
    }

    public static BigDecimal toEur(String currency) {
        return RATES.get(currency);
    }

    public static void override(String currency, BigDecimal rate) {
        RATES.put(currency, rate);
    }
}
