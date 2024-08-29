package org.voidzero.pgauditor;

import org.voidzero.influx.cli.InfluxCli;
import org.voidzero.influx.jdbc.InfluxConnection;

import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException {
        InfluxCli cli = new InfluxCli();
        Configuration config = (Configuration) cli.bindOrExit(Configuration.class, args);

        try (InfluxConnection connection = new InfluxConnection(DriverManager.getConnection(config.getConnectionString(), config.getUsername(), config.getPassword()))) {
            PgAuditor pgAuditor = new PgAuditor(connection, config);
            pgAuditor.run();
            System.out.println(pgAuditor);
        }
    }
}
