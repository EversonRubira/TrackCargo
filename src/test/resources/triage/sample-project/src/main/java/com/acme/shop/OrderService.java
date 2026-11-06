package com.acme.shop;

/** Fixture do retriever: servico de encomendas. */
public class OrderService {

    private final OrderRepository repository = null;

    public String ship(String status) {
        // linha 9
        // linha 10
        // linha 11
        // linha 12
        // linha 13
        if (!"PAID".equals(status)) {
            throw new InvalidTransitionException("Cannot ship order in status " + status);
        }
        return "SHIPPED";
    }

    public void validate(String name) {
        name.length();
    }

    // linha 22
    // linha 23
    // linha 24
}
