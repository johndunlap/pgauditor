package org.voidzero.pgauditor;

public class PgAuditor implements Runnable {
    private final Configuration configuration;

    public PgAuditor(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        System.out.println(configuration);
    }
}
