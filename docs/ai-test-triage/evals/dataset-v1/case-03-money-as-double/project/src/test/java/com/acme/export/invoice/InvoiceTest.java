package com.acme.export.invoice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceTest {

    @Test
    void totalOfASingleLineIsThatLine() {
        Invoice invoice = new Invoice(List.of(125.50));

        assertEquals(125.50, invoice.total());
    }

    @Test
    void totalOfThreeSmallLinesIsExact() {
        Invoice invoice = new Invoice(List.of(0.10, 0.10, 0.10));

        assertEquals(0.30, invoice.total());
    }
}
