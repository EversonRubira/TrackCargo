package com.acme.export.promotion;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionTest {

    @Test
    void promotionFromThePastIsNotActive() {
        Promotion promotion = new Promotion("OLD-2020", LocalDate.of(2020, 12, 31));

        assertFalse(promotion.isActive());
    }

    @Test
    void autumnPromotionIsStillActive() {
        Promotion promotion = new Promotion("AUTUMN-26", LocalDate.of(2026, 10, 31));

        assertTrue(promotion.isActive());
    }
}
