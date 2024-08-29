package org.voidzero;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.voidzero.influx.cli.InfluxCli;
import org.voidzero.influx.cli.exception.HelpException;
import org.voidzero.influx.cli.exception.ParseException;
import org.voidzero.influx.jdbc.InfluxConnection;
import org.voidzero.pgauditor.Configuration;
import org.voidzero.pgauditor.PgAuditor;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void testAuditUserTable() throws SQLException, HelpException, ParseException {
        try(InfluxConnection connection = connect(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // Create the table which should be audited
            assertFalse(connection.execute(CREATE_TABLE));
            assertEquals(connection.getInteger("select count(*) from public.account;"), Integer.valueOf(0));

            // Initialize PgAuditor
            InfluxCli cli = new InfluxCli();
            String[] args = {
                    "--table", "public.account",
                    "--username", POSTGRES.getUsername(),
                    "--password", POSTGRES.getPassword(),
                    "--dbname", POSTGRES.getDatabaseName(),
                    "--host", POSTGRES.getHost(),
                    "--port", POSTGRES.getMappedPort(5432).toString()
            };
            Configuration configuration = (Configuration) cli.bind(Configuration.class, args);
            PgAuditor pgAuditor = new PgAuditor(connection, configuration);
            pgAuditor.run();
            String ddl = pgAuditor.toString();
            System.err.println(ddl);

            // Create the audit table and triggers
            connection.execute(ddl);

            // Insert into the newly audited table
            connection.execute("insert into public.account(id, username) values(?, ?)", 1, "myuser");

            // Verify the column count and data types of the audit table
            String audColumnsQuery = """
                SELECT
                    column_name,
                    data_type
                FROM information_schema.columns
                WHERE table_name = 'aud_account'
                  AND table_schema = 'public'
                ORDER BY ordinal_position;
            """;

            List<Map<String, Object>> audColumns = connection.getListMap(audColumnsQuery);
            assertEquals(8, audColumns.size());
            assertEquals("audit_id", audColumns.get(0).get("column_name"));
            assertEquals("bigint", audColumns.get(0).get("data_type"));
            assertEquals("operation", audColumns.get(1).get("column_name"));
            assertEquals("USER-DEFINED", audColumns.get(1).get("data_type"));
            assertEquals("changed_by", audColumns.get(2).get("column_name"));
            assertEquals("text", audColumns.get(2).get("data_type"));
            assertEquals("changed_at", audColumns.get(3).get("column_name"));
            assertEquals("timestamp with time zone", audColumns.get(3).get("data_type"));
            assertEquals("old_id", audColumns.get(4).get("column_name"));
            assertEquals("bigint", audColumns.get(4).get("data_type"));
            assertEquals("new_id", audColumns.get(5).get("column_name"));
            assertEquals("bigint", audColumns.get(5).get("data_type"));
            assertEquals("old_username", audColumns.get(6).get("column_name"));
            assertEquals("text", audColumns.get(6).get("data_type"));
            assertEquals("new_username", audColumns.get(7).get("column_name"));
            assertEquals("text", audColumns.get(7).get("data_type"));

            // Insert a row of data
            Map<String, Object> row = connection.getMap("select id, username from public.account where id = 1");
            assertEquals(1L, row.get("id"));
            assertEquals("myuser", row.get("username"));

            // Verify that the insert into account also created a record in aud_account
            assertEquals(connection.getInteger("select count(*) from public.aud_account"), Integer.valueOf(1));

            Map<String, Object> values = connection.getMap("select * from public.aud_account");
            assertNotNull(values);
            assertEquals(8, values.size());
            assertEquals(1L, values.get("audit_id"));
            assertEquals("test", values.get("changed_by"));
            assertEquals("INSERT", values.get("operation"));
            assertNotNull(values.get("changed_at"));
            assertNull(values.get("old_id"));
            assertEquals(1L, values.get("new_id"));
            assertNull(values.get("old_username"));
            assertEquals("myuser", values.get("new_username"));
        }
    }
}
