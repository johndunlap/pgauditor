package org.voidzero.pgauditor;

import org.voidzero.influx.cli.InfluxCli;

public class Main {
    public static void main(String[] args) {
        InfluxCli cli = new InfluxCli();
        Configuration configuration = (Configuration) cli.bindOrExit(Configuration.class, args);
        PgAuditor pgAuditor = new PgAuditor(configuration);
        pgAuditor.run();
        System.out.println(pgAuditor);
    }
}
