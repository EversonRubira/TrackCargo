package com.acme.export.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortRouterTest {

    private final PortRouter router = new PortRouter();

    @Test
    void routesGermanCargoThroughRotterdam() {
        assertEquals("ROTTERDAM", router.portFor("DE"));
    }
}
