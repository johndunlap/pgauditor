package org.voidzero.pgauditor;

import org.voidzero.influx.jdbc.ColumnMetadata;
import org.voidzero.influx.jdbc.InfluxConnection;
import org.voidzero.influx.jdbc.TableMetadata;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.voidzero.pgauditor.Authentication.ANONYMOUS;
import static org.voidzero.pgauditor.Authentication.APPLICATION;
import static org.voidzero.pgauditor.Authentication.DATABASE;

public class PgAuditor {
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
     * The current user who has modified the database. This is only applicable in {@link Authentication} mode
     * APPLICATION.
     */
    private static final String AUTH_PROPERTY_NAME = "pgauditor.current_user";

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
    private final String auditFunctionName;

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
     * The database connection which should be introspected.
     */
    private final InfluxConnection connection;

    /**
     * This is used to capture the DDL output.
     */
    private final StringBuilder ddl = new StringBuilder();

    /**
     * Construct a new instance of this class.
     *
     * @param config User supplied parameters which are used to generate DDL
     */
    public PgAuditor(final InfluxConnection connection, final Configuration config) {
        this.connection = connection;
        this.config = config;
        this.auditTableName = "aud_" + config.getTableOnly();
        this.insertTriggerName = this.auditTableName + "_insert_trigger";
        this.updateTriggerName = this.auditTableName + "_update_trigger";
        this.deleteTriggerName = this.auditTableName + "_delete_trigger";
        this.auditFunctionName = "audit_" + config.getTableOnly() + "_changes";
    }

    public void run() throws SQLException {
        // Gather metadata about the table which will be audited
        TableMetadata tableMetadata = connection.getMetaData()
                .getTable(config.getSchema(), config.getTableOnly());

        dropTriggers(connection);

        if (config.getDrop()) {
            // We don't need to drop anything here because the triggers have already been dropped and tables, once
            // created, are never dropped for safety reasons. The sequence and enum type are never dropped because they
            // very little space and leaving avoids the complexity of determining if they are still in use.
            return;
        }

        createPgAuditorSettingFunction();
        createSequence();
        createEnumType();
        createAuditTable();
        createAuditFunction();
        createTriggers();
    }

    private void createTriggers() {
        String schema = config.getSchema();
        String table = config.getTableOnly();
        ddl.append("""
        CREATE TRIGGER %s AFTER INSERT ON %s.%s FOR EACH ROW EXECUTE PROCEDURE %s.%s();
        CREATE TRIGGER %s AFTER UPDATE ON %s.%s FOR EACH ROW EXECUTE PROCEDURE %s.%s();
        CREATE TRIGGER %s AFTER DELETE ON %s.%s FOR EACH ROW EXECUTE PROCEDURE %s.%s();
        """.formatted(
                insertTriggerName,
                schema,
                table,
                schema,
                auditFunctionName,
                updateTriggerName,
                schema,
                table,
                schema,
                auditFunctionName,
                deleteTriggerName,
                schema,
                table,
                schema,
                auditFunctionName
        ));
    }

    private List<Map<String, Object>> getColumns(final String schema, final String table) throws SQLException {
        String sql = """
            SELECT
                column_name,
                data_type as column_type
            FROM information_schema.columns
            WHERE table_name = ?
              AND table_schema = ?
            ORDER BY ordinal_position
        """;

        return connection.getListMap(sql, table, schema);
    }

    private void createAuditFunction() throws SQLException {
        String schema = config.getSchema();
        String tableName = config.getTableOnly();
        String authenticationCheck;

        if (config.getAuthentication().equals(APPLICATION)) {
            // This fragment is only necessary for APPLICATION authentication where the current user must be
            // identified by the client application prior to modifying the database
            authenticationCheck = """
                            SELECT INTO changed_by_var pgauditor_get_setting('%s');
                            IF changed_by_var is null or trim(changed_by_var) = '' THEN
                                RAISE EXCEPTION 'Anonymous updates are not permitted for audited table %s.%s. To identify the user making the change run: SET [LOCAL] "%s"=<user>';
                            END IF;
                    """.formatted(
                        AUTH_PROPERTY_NAME,
                        schema,
                        tableName,
                        AUTH_PROPERTY_NAME
                    );
        } else if (config.getAuthentication().equals(DATABASE)) {
            authenticationCheck = "select into changed_by_var current_user;";
        } else if (config.getAuthentication().equals(ANONYMOUS)) {
            authenticationCheck = "";
        } else {
            throw new RuntimeException("Unsupported authentication type: " + config.getAuthentication());
        }

        StringBuilder columnDeclarations = new StringBuilder();
        StringBuilder captureUpdates = new StringBuilder();
        StringBuilder captureInserts = new StringBuilder();
        StringBuilder captureDeletes = new StringBuilder();
        StringBuilder insertColumnNames = new StringBuilder();
        StringBuilder insertColumnValues = new StringBuilder();

        for (Map<String, Object> column : getColumns(schema, tableName)) {
            String columnName = (String) column.get("column_name");
            String columnType = (String) column.get("column_type");

            columnDeclarations.append("""
                old_%s_var %s := NULL;
                new_%s_var %s := NULL;
            """.formatted(
                    columnName,
                    columnType,
                    columnName,
                    columnType
            ));

            captureUpdates.append("""
                    IF ((OLD.%s IS NULL and NEW.%s IS NOT NULL) or (OLD.%s IS NOT NULL and NEW.%s IS NULL) or (OLD.%s != NEW.%s)) THEN
                        old_%s_var := OLD.%s;
                        new_%s_var := NEW.%s;
                        change_count := change_count + 1;
                    END IF;
            """.replaceAll("%s", columnName));

            captureInserts.append("""
                    new_%s_var := NEW.%s;
            """.replaceAll("%s", columnName));

            captureDeletes.append("""
                    old_%s_var := OLD.%s;
            """.replaceAll("%s", columnName));

            insertColumnNames.append("""
                        ,old_%s
                        ,new_%s
            """.replaceAll("%s", columnName));

            insertColumnValues.append("""
                        ,old_%s_var
                        ,new_%s_var
            """.replaceAll("%s", columnName));
        }

        StringBuilder auditTableInsert = new StringBuilder("""
                INSERT INTO %s.%s(
                    audit_id
                    ,operation
                    ,changed_by
                    ,changed_at
        """.formatted(schema, auditTableName));

        if (config.getApplicationName()) {
            auditTableInsert.append("            ,application_name\n");
        }

        auditTableInsert.append(insertColumnNames).append("""
                ) values(
                    nextval('%s.%s')
                    ,operation_var
                    ,changed_by_var
                    ,changed_at_var
        """.formatted(schema, SEQUENCE_NAME));

        if (config.getApplicationName()) {
            auditTableInsert.append("            ,pgauditor_get_setting('application_name')\n");
        }

        auditTableInsert.append(insertColumnValues).append("        );\n");

        // I'm not using a string builder here because it would make the audit function unreadable
        String createTriggerFunction = """
        \nCREATE OR REPLACE FUNCTION %s() RETURNS TRIGGER
        AS
        $BODY$
        DECLARE
            changed_by_var text := NULL;
            changed_at_var timestamp with time zone := current_timestamp;
            operation_var %s.%s := NULL;
        %s
            change_count INT := 0;
        BEGIN
            operation_var=TG_OP::%s.%s;
            %s
            IF (operation_var = 'UPDATE') THEN
        %s
            ELSIF (operation_var = 'INSERT') THEN
        %s
                change_count := change_count + 1;
            ELSIF (operation_var = 'DELETE') THEN
        %s
                change_count := change_count + 1;
            ELSE
                raise exception 'Unknown operation: %%', operation_var;
            END IF;

            IF change_count > 0 THEN
        %s
            END IF;
            RETURN NULL;
        END
        $BODY$
        LANGUAGE plpgsql VOLATILE;
        """.formatted(
                auditFunctionName,
                schema,
                ENUM_TYPE_NAME,
                columnDeclarations.toString(),
                schema,
                ENUM_TYPE_NAME,
                authenticationCheck,
                captureUpdates.toString(),
                captureInserts.toString(),
                captureDeletes.toString(),
                auditTableInsert
        );

        ddl.append(createTriggerFunction);
    }

    private void createAuditTable() throws SQLException {
        String schema = config.getSchema();

        // Abort if the table already exists
        if (tableExists(connection, config.getSchema(), auditTableName)) {
            // TODO: Replace this query with data taken from table metadata
            String missingColumnQuery = """
            SELECT
                replace(column_name, 'new_', '') as column_name,
                data_type
            FROM information_schema.columns
            WHERE table_name = '%s'
                AND table_schema = '%s'
                AND column_name not like 'old_%%'
                AND column_name not in(
                    'audit_id',
                    'changed_by',
                    'changed_at',
                    'operation'
                )
            ORDER BY ordinal_position
        """.formatted(auditTableName, schema);
            // TODO: Add missing columns
            throw new RuntimeException("IMPLEMENT ME");
        }

        // TODO: Replace this query with data taken from table metadata
        String columnQuery = """
            SELECT
                column_name,
                data_type
            FROM information_schema.columns
            WHERE table_name = '%s'
              AND table_schema = '%s'
            ORDER BY ordinal_position
        """.formatted(config.getTableOnly(), schema);

        List<Map<String, Object>> columns = connection.getListMap(columnQuery);

        // Create audit table
        ddl.append("""
        \nCREATE TABLE IF NOT EXISTS %s.%s(
            audit_id bigint UNIQUE NOT NULL DEFAULT nextval('%s.%s')
            ,operation %s.%s
            ,changed_by text
            ,changed_at timestamp with time zone
        """.formatted(schema, auditTableName, schema, SEQUENCE_NAME, schema, ENUM_TYPE_NAME));

        if (config.getApplicationName()) {
            ddl.append("    ,application_name text\n");
        }

        // TODO: Support capturing the entire row when it changes as opposed to just the values that changed
        for (Map<String, Object> column : columns) {
            ddl.append("    ,old_%s %s\n".formatted(column.get("column_name"), column.get("data_type")));
            ddl.append("    ,new_%s %s\n".formatted(column.get("column_name"), column.get("data_type")));
        }

        ddl.append(");\n");
    }

    private boolean tableExists(final InfluxConnection connection, final String schema, final String table) throws SQLException {
        TableMetadata tableMetaData = connection.getMetaData().getTable(schema, table);
        return tableMetaData != null;
    }

    private void createEnumType() throws SQLException {
        if (!enumTypeExists(connection, config.getSchema(), ENUM_TYPE_NAME)) {
            ddl.append("CREATE TYPE ")
                    .append(config.getSchema())
                    .append(".")
                    .append(ENUM_TYPE_NAME)
                    .append(" AS ENUM ('INSERT', 'UPDATE', 'DELETE');\n");
        }
    }

    private void createSequence() throws SQLException {
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

    private void createPgAuditorSettingFunction() throws SQLException {
        if (!functionExists(connection, config.getSchema(), SETTINGS_FUNCTION_NAME)) {
            ddl.append("CREATE OR REPLACE FUNCTION ")
                    .append(config.getSchema())
                    .append(".")
                    .append(SETTINGS_FUNCTION_NAME)
                    .append("(")
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
                "        AND trg.tgrelid::text = ? " +
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
