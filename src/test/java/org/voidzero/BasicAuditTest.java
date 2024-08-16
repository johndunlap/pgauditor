package org.voidzero;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.voidzero.influx.jdbc.InfluxConnection;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
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
}
