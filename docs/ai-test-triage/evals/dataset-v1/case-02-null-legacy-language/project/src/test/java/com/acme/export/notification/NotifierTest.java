package com.acme.export.notification;

import com.acme.export.customer.Customer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotifierTest {

    private final Notifier notifier = new Notifier();

    @Test
    void writesInPortugueseForPortugueseCustomers() {
        Customer customer = new Customer("Atlantic Foods", "pt-BR");

        assertEquals("Documento disponivel: invoice", notifier.subjectFor(customer, "invoice"));
    }

    @Test
    void legacyCustomerGetsTheEnglishSubject() {
        Customer legacyCustomer = new Customer("Old Harbour Ltd", null);

        assertEquals("Document available: invoice", notifier.subjectFor(legacyCustomer, "invoice"));
    }
}
