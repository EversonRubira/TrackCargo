package com.acme.export.consignment;

public class Consignment {

    private final String id;
    private final Status status;
    private boolean cancelled;

    public Consignment(String id, Status status) {
        this.id = id;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public Status status() {
        return status;
    }

    public boolean cancelled() {
        return cancelled;
    }

    void markCancelled() {
        this.cancelled = true;
    }
}
