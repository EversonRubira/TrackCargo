package com.acme.export.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StoragePolicyTest {

    private final StoragePolicy policy = new StoragePolicy();

    @Test
    void freeStorageLasts7Days() {
        assertEquals(7, policy.freeDays());
    }

    @Test
    void storageBeyondTheFreePeriodIsCharged() {
        assertFalse(policy.isFree(11));
    }
}
