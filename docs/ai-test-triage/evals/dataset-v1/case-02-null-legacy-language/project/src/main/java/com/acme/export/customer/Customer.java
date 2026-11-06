package com.acme.export.customer;

/** Cliente importador. Clientes antigos foram migrados sem idioma preferido. */
public class Customer {

    private final String name;
    private final String language;

    public Customer(String name, String language) {
        this.name = name;
        this.language = language;
    }

    public String name() {
        return name;
    }

    public String language() {
        return language;
    }
}
