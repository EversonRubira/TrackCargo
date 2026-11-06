package com.acme.export.amount;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountParserTest {

    private final AmountParser parser = new AmountParser();

    @Test
    void parsesAPlainDecimal() {
        assertEquals(new BigDecimal("1234.50"), parser.parse("1234.50"));
    }

    @Test
    void parsesABrazilianFormattedAmount() {
        assertEquals(new BigDecimal("1234.50"), parser.parse("1.234,50"));
    }
}
