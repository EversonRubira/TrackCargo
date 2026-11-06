# Export operations specification

## Consignment lifecycle

A consignment moves through DRAFT, BOOKED, LOADED, SHIPPED and DELIVERED, in this order.

Cancellation rule: a consignment can be cancelled while it is DRAFT or BOOKED. From LOADED onwards
it can no longer be cancelled, because the cargo is already physically in the container.
Cancelling an already cancelled consignment does nothing.

## Invoicing

Invoices are issued when the consignment reaches SHIPPED.
