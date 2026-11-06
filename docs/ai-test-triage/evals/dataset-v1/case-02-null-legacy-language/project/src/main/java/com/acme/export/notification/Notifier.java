package com.acme.export.notification;

import com.acme.export.customer.Customer;

public class Notifier {

    public String subjectFor(Customer customer, String documentName) {
        if (customer.language().toLowerCase().startsWith("pt")) {
            return "Documento disponivel: " + documentName;
        }
        return "Document available: " + documentName;
    }
}
