package com.acme.export.consignment;

public class ConsignmentService {

    /** Cancela uma carga. So e permitido antes de a carga ser carregada. */
    public void cancel(Consignment consignment) {
        if (consignment.cancelled()) {
            return;
        }
        if (consignment.status().ordinal() >= Status.BOOKED.ordinal()) {
            throw new InvalidTransitionException("Cannot cancel consignment " + consignment.id()
                    + " in status " + consignment.status());
        }
        consignment.markCancelled();
    }
}
