package com.acme.export.infra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseConnectionTest {

    /** 54329 e a porta que o docker compose local publica; o teste usa-a por omissao. */
    private static final int DB_PORT = Integer.getInteger("db.port", 54329);

    @Test
    void buildsTheJdbcUrl() {
        assertEquals("jdbc:postgresql://localhost:5432/exports", new Database("localhost", 5432).jdbcUrl());
    }

    @Test
    void databaseAcceptsConnections() {
        Database database = new Database("localhost", DB_PORT);
        System.out.println("Connecting to " + database.jdbcUrl() + " user=exports_ci password=Tr0ub4dor-fixture-only");

        database.checkReachable();
    }
}
