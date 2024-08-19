package org.voidzero;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.voidzero.influx.cli.InfluxCli;
import org.voidzero.influx.jdbc.InfluxConnection;
import org.voidzero.pgauditor.Configuration;
import org.voidzero.pgauditor.PgAuditor;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.voidzero.influx.jdbc.InfluxConnection.connect;

/**
 * This test will fail to run unless docker can be executed without sudo. For example, on Ubuntu based systems, this
 * can be done as follows:<br/>
 * <pre>
 * sudo groupadd docker
 * sudo gpasswd -a $USER docker
 * sudo service docker restart
 * sudo shutdown -r now
 * </pre>
 *
 * The command "docker ps" should execute correctly without sudo. TestContainers will fail if it doesn't.
 */
public class BasicAuditTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CREATE_TABLE = """
        create table public.account(
            id bigserial primary key,
            username text unique not null
        );
     """;

    @BeforeClass
    public static void beforeClass() {
        POSTGRES.start();
    }

    @AfterClass
    public static void afterClass() {
        POSTGRES.stop();
    }

    @Test
    public void testGetIntegerMethod() throws SQLException {
        try(InfluxConnection connection = connect(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            int expected = 7;
            int actual = connection.getInteger("select 7");
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testAuditUserTable() throws SQLException {
        try(InfluxConnection connection = connect(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertFalse(connection.execute(CREATE_TABLE));

            InfluxCli cli = new InfluxCli();
            String[] args = {
                    "--table", "public.account",
                    "--username", POSTGRES.getUsername(),
                    "--password", POSTGRES.getPassword(),
                    "--dbname", POSTGRES.getDatabaseName(),
                    "--host", POSTGRES.getHost(),
                    "--port", POSTGRES.getMappedPort(5432).toString()
            };
            Configuration configuration = (Configuration) cli.bindOrExit(Configuration.class, args);
            PgAuditor pgAuditor = new PgAuditor(configuration);
            pgAuditor.run();
            String ddl = pgAuditor.toString();

            connection.execute(ddl);

            assertEquals(connection.getInteger("select count(*) from public.aud_account;"), Integer.valueOf(0));
        }
    }
}
