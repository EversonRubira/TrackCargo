package com.acme.export.rates;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeRatesTest {

    @Test
    void overriddenRateIsUsed() {
        ExchangeRates.override("USD", new BigDecimal("1.10"));

        assertEquals(new BigDecimal("1.10"), ExchangeRates.toEur("USD"));
    }

    @Test
    void defaultRateForUsd() {
        assertEquals(new BigDecimal("0.90"), ExchangeRates.toEur("USD"));
    }
}
