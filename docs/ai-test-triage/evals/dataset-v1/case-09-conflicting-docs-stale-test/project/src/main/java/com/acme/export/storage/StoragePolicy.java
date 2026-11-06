package com.acme.export.storage;

public class StoragePolicy {

    /** Dias de armazenamento gratuito no porto de destino (ADR-007). */
    private static final int FREE_DAYS = 10;

    public int freeDays() {
        return FREE_DAYS;
    }

    public boolean isFree(int daysStored) {
        return daysStored <= FREE_DAYS;
    }
}
