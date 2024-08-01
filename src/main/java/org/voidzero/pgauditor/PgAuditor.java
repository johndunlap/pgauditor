package org.voidzero.pgauditor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PgAuditor implements Runnable {
    private final Configuration config;

    public PgAuditor(Configuration config) {
        this.config = config;
    }

    @Override
    public void run() {
        System.out.printf("%s\n", config.getConnectionString());

        try (Connection connection = DriverManager.getConnection(config.getConnectionString(), config.getUsername(), config.getPassword())) {

            if (connection != null) {
                System.out.println("Connected to the database!");

                TableMetadata table = new TableMetadata(connection, config);
                System.out.println(table);

                for (ColumnMetadata column : table.getColumns()) {
                    System.out.println(column);
                }
            } else {
                System.out.println("Failed to make connection!");
            }
        } catch (SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
        }
    }
}
