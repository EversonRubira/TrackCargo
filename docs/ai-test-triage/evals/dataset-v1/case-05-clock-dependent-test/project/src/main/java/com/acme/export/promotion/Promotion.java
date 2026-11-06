package com.acme.export.promotion;

import java.time.LocalDate;

public class Promotion {

    private final String code;
    private final LocalDate validUntil;

    public Promotion(String code, LocalDate validUntil) {
        this.code = code;
        this.validUntil = validUntil;
    }

    public String code() {
        return code;
    }

    /** Ativa ate ao fim do ultimo dia de validade, pelo relogio do sistema. */
    public boolean isActive() {
        return !LocalDate.now().isAfter(validUntil);
    }
}
