package com.acme.export.invoice;

import java.util.List;

public class Invoice {

    private final List<Double> lineAmounts;

    public Invoice(List<Double> lineAmounts) {
        this.lineAmounts = List.copyOf(lineAmounts);
    }

    public double total() {
        double sum = 0;
        for (double amount : lineAmounts) {
            sum += amount;
        }
        return sum;
    }
}
