package com.shenliwei.devicewatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaSmokeTest {

    @Test
    void schemaCreatesRequiredTables() throws Exception {
        InputStream schemaStream = getClass().getResourceAsStream("/db/schema.sql");
        assertNotNull(schemaStream, "schema.sql must be available on the classpath");

        String schema;
        try (schemaStream) {
            schema = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";\\s*\\R")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }

            Set<String> tables = new HashSet<>();
            try (ResultSet result = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }

            assertTrue(
                    tables.containsAll(
                            Set.of("alerts", "devices", "operators", "schema_version", "telemetry")),
                    () -> "Missing required tables: " + tables);
        }
    }
}
