package com.acme.export.consignment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsignmentServiceTest {

    private final ConsignmentService service = new ConsignmentService();

    @Test
    void cancelsADraftConsignment() {
        Consignment consignment = new Consignment("C-100", Status.DRAFT);

        service.cancel(consignment);

        assertTrue(consignment.cancelled());
    }

    @Test
    void cancelsABookedConsignment() {
        Consignment consignment = new Consignment("C-101", Status.BOOKED);

        service.cancel(consignment);

        assertTrue(consignment.cancelled());
    }

    @Test
    void refusesToCancelALoadedConsignment() {
        Consignment consignment = new Consignment("C-102", Status.LOADED);

        assertThrows(InvalidTransitionException.class, () -> service.cancel(consignment));
    }
}
