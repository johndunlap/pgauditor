package org.voidzero.pgauditor;

import org.voidzero.influx.jdbc.InfluxConnection;
import org.voidzero.influx.jdbc.TableMetadata;

import java.sql.DriverManager;
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
     * The name of the function which will obtain configuration settings. A custom function is necessary for error
     * trapping.
     */
    private static final String SETTINGS_FUNCTION_NAME = "pgauditor_get_setting";

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
     * The name of the function which is invoked by the audit triggers.
     */
    private String auditFunctionName;

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

    /**
     * This is used to capture the DDL output.
     */
    private final StringBuilder ddl = new StringBuilder();

    /**
     * Construct a new instance of this class.
     *
     * @param config User supplied parameters which are used to generate DDL
     */
    public PgAuditor(final Configuration config) {
        this.config = config;
        this.auditTableName = "aud_" + config.getTableOnly();
        this.insertTriggerName = this.auditTableName + "_insert_trigger";
        this.updateTriggerName = this.auditTableName + "_update_trigger";
        this.deleteTriggerName = this.auditTableName + "_delete_trigger";
        this.auditFunctionName = "audit_" + config.getTableOnly() + "_changes";
    }

    @Override
    public void run() {
        try (InfluxConnection connection = new InfluxConnection(DriverManager.getConnection(config.getConnectionString(), config.getUsername(), config.getPassword()))) {
            // Gather metadata about the table which will be audited
            TableMetadata tableMetadata = connection.getMetaData()
                    .getTable(config.getSchema(), config.getTableOnly());

            createPgAuditorSettingFunction(connection);
            dropTriggers(connection);
            createSequence(connection);
            createEnumType(connection);

            if (config.getDrop()) {
                return;
            }

            // TODO: Create audit table etc
        } catch (SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
        }
    }

    private void createEnumType(final InfluxConnection connection) throws SQLException {
        if (!enumTypeExists(connection, config.getSchema(), ENUM_TYPE_NAME)) {
            ddl.append("CREATE TYPE ")
                    .append(config.getSchema())
                    .append(".")
                    .append(ENUM_TYPE_NAME)
                    .append(" AS ENUM ('INSERT', 'UPDATE', 'DELETE');\n");
        }
    }

    private void createSequence(final InfluxConnection connection) throws SQLException {
        if (!sequenceExists(connection, config.getSchema(), SEQUENCE_NAME)) {
            ddl.append("CREATE SEQUENCE IF NOT EXISTS ")
                    .append(config.getSchema())
                    .append(".")
                    .append(SEQUENCE_NAME)
                    .append(";\n");
        }
    }

    private void dropTriggers(final InfluxConnection connection) throws SQLException {
        dropTriggerIfExists(connection, insertTriggerName, config.getSchema(), config.getTableOnly());
        dropTriggerIfExists(connection, updateTriggerName, config.getSchema(), config.getTableOnly());
        dropTriggerIfExists(connection, deleteTriggerName, config.getSchema(), config.getTableOnly());
    }

    private void createPgAuditorSettingFunction(final InfluxConnection connection) throws SQLException {
        if (!functionExists(connection, config.getSchema(), SETTINGS_FUNCTION_NAME)) {
            ddl.append("CREATE OR REPLACE FUNCTION ")
                    .append(config.getSchema())
                    .append(".")
                    .append(SETTINGS_FUNCTION_NAME)
                    .append("name text) RETURNS TEXT\n")
                    .append("LANGUAGE plpgsql\n")
                    .append("  AS $BODY$\n")
                    .append("  DECLARE\n")
                    .append("      value text;\n")
                    .append("  BEGIN\n")
                    .append("      SELECT INTO value current_setting(NAME);\n")
                    .append("          RETURN value;\n")
                    .append("  EXCEPTION WHEN OTHERS THEN\n")
                    .append("      RETURN NULL;\n")
                    .append("  END;\n")
                    .append("  $BODY$ VOLATILE;\n");
        }
    }

    private void dropTriggerIfExists(final InfluxConnection connection, final String triggerName, final String schemaName, final String tableName) throws SQLException {
        String query = "SELECT EXISTS ( " +
                "      SELECT 1 " +
                "      FROM pg_trigger trg " +
                "        INNER JOIN pg_class tbl ON trg.tgrelid = tbl.oid " +
                "        INNER JOIN pg_namespace n ON tbl.relnamespace = n.oid " +
                "      WHERE trg.tgname = ? " +
                "        AND trg.tgrelid = ?::regclass " +
                "        AND n.nspname = ?" +
                "  );";

        boolean exists = connection.getBoolean(
                query,
                triggerName,
                tableName,
                schemaName
        );

        if (exists) {
            ddl.append("DROP TRIGGER IF EXISTS ")
                    .append(triggerName)
                    .append(" ON ")
                    .append(schemaName)
                    .append(".")
                    .append(tableName)
                    .append(";\n");
        }
    }

    private void dropFunctionIfExists(final InfluxConnection connection, final String schemaName, final String functionName) throws SQLException {
        if (functionExists(connection, schemaName, functionName)) {
            ddl.append("DROP FUNCTION IF EXISTS ")
                    .append(schemaName)
                    .append(".")
                    .append(functionName)
                    .append(";\n");
        }
    }

    private boolean functionExists(final InfluxConnection connection, final String schemaName, final String functionName) throws SQLException {
        String query = "SELECT EXISTS ( " +
                "    select 1 " +
                "    from pg_proc p " +
                "        inner join pg_namespace n ON p.pronamespace = n.oid " +
                "    where p.proname = ? " +
                "        and n.nspname = ? " +
                "  );";

        return connection.getBoolean(
                query,
                functionName,
                schemaName
        );
    }

    private boolean sequenceExists(final InfluxConnection connection, final String schemaName, final String sequenceName) throws SQLException {
        String query = "SELECT EXISTS ( " +
                "        SELECT 1 " +
                "        FROM pg_sequences " +
                "        WHERE sequencename = ? " +
                "            AND schemaname = ? " +
                "    );";

        return connection.getBoolean(
                query,
                sequenceName,
                schemaName
        );
    }

    private boolean enumTypeExists(final InfluxConnection connection, final String schemaName, final String enumTypeName) throws SQLException {
        String query = "SELECT EXISTS ( " +
                "      SELECT 1 " +
                "    FROM pg_type pt " +
                "        INNER JOIN pg_namespace pn ON pt.typnamespace = pn.oid " +
                "    WHERE pt.typname = ? " +
                "        AND pt.typtype = 'e' " +
                "        AND pn.nspname = ? " +
                "  );";

        return connection.getBoolean(
                query,
                enumTypeName,
                schemaName
        );
    }

    @Override
    public String toString() {
        return ddl.toString();
    }
}
