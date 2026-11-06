package com.acme.export.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerNumberTest {

    @Test
    void ownerCodeIsTheFirstFourLetters() {
        assertEquals("MSKU", new ContainerNumber("MSKU1234567").ownerCode());
    }

    @Test
    void serialIsTheDigitsAfterTheOwnerCode() {
        assertEquals("1234567", new ContainerNumber("MSKU1234567").serial());
    }
}
