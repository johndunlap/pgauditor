package org.voidzero.pgauditor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PgAuditor implements Runnable {
    /**
     * The name of the enum type which is used to identify the type of operation which created the audit record.
     */
    private static final String ENUM_TYPE_NAME = "pgauditor_operation";

    /**
     * The name of the sequence which should be used to uniquely identify each row in the audit table. This sequence is
     * used to generate the value for the audit_id column. The audit_id column is a primary key and is used to identify
     * a row in the audit table and to reconstruct the natural order of modifications across multiple audit tables.
    */
    private static final String SEQUENCE_NAME = "pgauditor_audit_seq";

    /**
     * Configuration object which contains all configuration parameters which were extracted from environment variables
     * and parsed from command line arguments.
     */
    private final Configuration config;

    /**
     * The name of the generated audit table.
     */
    private final String auditTableName;

    /**
     * The name of the trigger which will fire when a record is inserted into the specified table.
     */
    private final String insertTriggerName;

    /**
     * The name of the trigger which will fire when a record is updated in the specified table.
     */
    private final String updateTriggerName;

    /**
     * The name of the trigger which will fire when a record is deleted from the specified table.
     */
    private final String deleteTriggerName;

    public PgAuditor(final Configuration config) {
        this.config = config;
        this.auditTableName = "aud_" + config.getTableOnly();
        this.insertTriggerName = this.auditTableName + "_insert_trigger";
        this.updateTriggerName = this.auditTableName + "_update_trigger";
        this.deleteTriggerName = this.auditTableName + "_delete_trigger";
    }

    @Override
    public void run() {
        try (Connection connection = DriverManager.getConnection(config.getConnectionString(), config.getUsername(), config.getPassword())) {
            if (connection != null) {
                TableMetadata table = new TableMetadata(connection, config);

                System.out.println(table);

                for (ColumnMetadata column : table.getColumns()) {
                    System.out.println(column);
                }
            } else {
                throw new SQLException("Failed to connect to database: " + config.getConnectionString());
            }
        } catch (SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
        }
    }

    private void dropTriggerIfExists(final Connection connection, final String triggerName, final String schemaName, final String tableName) throws SQLException {
        String existsSql = "SELECT EXISTS ( " +
                "      SELECT 1 " +
                "      FROM pg_trigger trg " +
                "        INNER JOIN pg_class tbl ON trg.tgrelid = tbl.oid " +
                "        INNER JOIN pg_namespace n ON tbl.relnamespace = n.oid " +
                "      WHERE trg.tgname = ? " +
                "        AND trg.tgrelid = ?::regclass " +
                "        AND n.nspname = ?" +
                "  );";
        PreparedStatement statement = connection.prepareStatement(existsSql);
        statement.setString(1, triggerName);
        statement.setString(2, tableName);
        statement.setString(3, schemaName);
        ResultSet resultSet = statement.executeQuery();

        if (!resultSet.next()) {
            throw new SQLException("Failed to execute query: " + existsSql);
        }

        boolean exists = resultSet.getBoolean(1);

        if (exists) {
            System.out.println("DROP TRIGGER IF EXISTS " + triggerName + " ON " + schemaName + "." + tableName + ";");
        }
    }
}
