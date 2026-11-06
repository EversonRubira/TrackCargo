package com.acme.export.routing;

public class PortRouter {

    private static final String EU_ROUTING_FLAG = "export.eu.routing";

    public String portFor(String destinationCountry) {
        boolean euRouting = Boolean.getBoolean(EU_ROUTING_FLAG);
        if (euRouting && "DE".equals(destinationCountry)) {
            return "ROTTERDAM";
        }
        return "HAMBURG";
    }
}
