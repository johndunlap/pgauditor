package org.voidzero.pgauditor;

import org.voidzero.influx.cli.annotation.Arg;
import org.voidzero.influx.cli.annotation.Command;
import org.voidzero.influx.cli.annotation.Ignore;

@Command(openingText = "PgAuditor: The simplest way to track changes in PostgreSQL databases.\n\nIMPORTANT: PgAuditor will not modify your database in any way regardless of the selected options. You can have confidence in this because PgAuditor passes readOnly=true to the PostgreSQL JDBC driver when establishing a database connection. If additional safety is required, you may provide read-only database credentials. Once connected, the specified table is introspected and DDL is printed to the console; Nothing more. If you want to execute the generated DDL, you must do so manually. This workflow is intended to give a human being the opportunity to sanity check the generated DDL prior to it being executed. Generated DDL will not drop audit tables or their columns under any circumstances. If previously captured data is no longer required, it must be purged manually.\n\nAdditional documentation:\nhttps://github.com/johndunlap/pgauditor\nhttps://jdbc.postgresql.org/documentation/use/\nhttps://www.postgresql.org/docs/current/libpq-envars.html\n\nPlease report issues here:\nhttps://github.com/johndunlap/pgauditor/issues\n\nThe following options are accepted:")
public class Configuration {
    @Arg(code = 't', flag = "table", required = true, description = "Name of the audited table. The table name may include a schema prefix. If no schema name is provided, the public schema will be assumed")
    private String rawTable;

    @Arg(code = 'a', flag = "auth", converter = AuthenticationTypeConverter.class, description = "Valid values: application, database, anonymous. This is the mechanism which is used to to identify the user in the audit log. If \"application\" is passed, a custom PostgreSQL configuration parameter, which must be populated by the client, is used to determine the user who is responsible for data modifications. The advantage of \"application\" is that it allows a single database user to be shared between multiple application level users without losing track of who modified what. The down side of \"application\" is that code changes are required in the client application to appropriately set and unset the configuration parameter within each PostgreSQL database connection. If \"database\" is passed, the current database username is used to determine the user who is responsible for data modifications. If \"anonymous\" is passed, authentication will be disabled entirely and, consequently, the audit log for the specified table will not contain the identity of the user responsible for data modifications. The default is \"database\".")
    private Authentication authentication = Authentication.DATABASE;

    @Arg(code = 'c', flag = "config-property", description = "Required when the --auth flag is set to \"application\" and ignored otherwise. When set, this is used as the name of the configuration parameter which will be used to determine the current user. The default value is \"pgauditor.current_user\".")
    private String configProperty = "pgauditor.current_user";

    @Arg(code = 'D', flag = "drop", description = "No argument required. Drops audit triggers and audit function for specified table. Audit table, sequence, and enum type are not dropped.")
    private Boolean drop = false;

    @Arg(code = 'n', flag = "application-name", description = "No argument required. When this flag is passed, the PostgreSQL application name will be captured in the audit table. This is useful for identifying which application made the change. The default is to not capture the application name.")
    private String applicationName;

    @Arg(code = 'v', flag = "version", description = "Prints the version of PgAuditor and the version of its bundled JDBC driver")
    private Boolean version = false;

    @Arg(code = 'V', flag = "verbose", description = "When specified, diagnostic information will be written to stderr")
    private Boolean verbose;

    // The codes, flags, and environment variable names below this line should match what is accepted by psql. This
    // should, in theory, reduce the learning curve for people who are already familiar with psql
    @Arg(code = 'h', flag = "host", environmentVariable = "PGHOST", description = "The network hostname of the database which should be connected to. Alternatively, the PGHOST environment variable can be used to pass this value. If both the flag and the environment variable have been set, the flag will take precedence over the environment variable.")
    private String hostname = "localhost";

    @Arg(code = 'U', flag = "username", required = true, environmentVariable = "PGUSER", description = "The username which should be used to authenticate against PostgreSQL. Alternatively, the PGUSER environment variable can be used to pass this value. If both the flag and the environment variable have been set, the flag will take precedence over the environment variable.")
    private String username;

    @Arg(code = 'd', flag = "dbname", environmentVariable = "PGDATABASE", description = "The name of the database which should be connected to. Alternatively, the PGDATABASE environment variable can be used to pass this value. If both the flag and the environment variable have been set, the flag will take precedence over the environment variable.")
    private String database;

    @Arg(code = 'p', flag = "port", environmentVariable = "PGPORT", description = "The network port number that PostgreSQL is listening on. The default is 5432. Alternatively, the PGPORT environment variable can be used to pass this value. If both the flag and an environment variable are set, the flag will take precedence over the environment variable.")
    private Integer port = 5432;

    @Arg(code = 'W', flag = "password", environmentVariable = "PGPASSWORD", description = "The password which should be used to authenticate against PostgreSQL. It is recommended that this be passed through the PGPASSWORD environment variable instead of through this flag because doing so stops the database password from appearing in your terminal history. If both the flag and the environment variable are set, the flag will take precedence over the environment variable.")
    private String password;

    @Ignore
    private String schema;

    @Ignore
    private String tableOnly;

    @Ignore
    private String tableWithSchema;

    @Ignore
    private String connectionString;

    public Configuration() {
    }

    public String getRawTable() {
        return rawTable;
    }

    public void setRawTable(String rawTable) {
        this.rawTable = rawTable;
        int index = rawTable.lastIndexOf('.');

        if (index > 0) {
            schema = rawTable.substring(0, index);
            tableOnly = rawTable.substring(index);
        } else {
            schema = "public";
            tableOnly = rawTable;
        }

        tableWithSchema = schema + "." + tableOnly;
    }

    public String getConfigProperty() {
        return configProperty;
    }

    public Boolean getDrop() {
        return drop;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public Boolean getVersion() {
        return version;
    }

    public String getHostname() {
        return hostname;
    }

    public String getUsername() {
        return username;
    }

    public String getDatabase() {
        return database;
    }

    public Integer getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public String getSchema() {
        return schema;
    }

    public String getTableOnly() {
        return tableOnly;
    }

    public String getTableWithSchema() {
        return tableWithSchema;
    }

    public Boolean getVerbose() {
        return verbose;
    }

    public String getConnectionString() {
        if(connectionString == null) {
            connectionString = "jdbc:postgresql://" +
                    (hostname == null ? "localhost" : hostname) +
                    ":" +
                    (port == null ? "5432" : port) +
                    "/" +
                    database +
                    "?readOnly=true&currentSchema=" +
                    schema;
        }
        return connectionString;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "table='" + rawTable + '\'' +
                ", authentication=" + authentication +
                ", configProperty='" + configProperty + '\'' +
                ", drop=" + drop +
                ", applicationName='" + applicationName + '\'' +
                ", version=" + version +
                ", hostname='" + hostname + '\'' +
                ", username='" + username + '\'' +
                ", database='" + database + '\'' +
                ", port=" + port +
                ", password='" + password + '\'' +
                '}';
    }
}
