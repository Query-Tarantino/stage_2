package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.common.operations.Lease;

import java.util.function.BooleanSupplier;

/** A lease that is lost only after a chosen number of checks and remembers its renewal and whether the job closed it. */
final class RecordedLease implements Lease {

    private BooleanSupplier renewal;
    private int started;
    private boolean closed;
    private int checksBeforeLoss = Integer.MAX_VALUE;
    private int checks;

    Lease start(BooleanSupplier renewal) {
        this.renewal = renewal;
        started++;
        return this;
    }

    boolean renew() {
        return renewal.getAsBoolean();
    }

    void loseAfterChecks(int checks) {
        checksBeforeLoss = checks;
    }

    int started() {
        return started;
    }

    boolean closed() {
        return closed;
    }

    @Override
    public boolean lost() {
        return ++checks > checksBeforeLoss;
    }

    @Override
    public void close() {
        closed = true;
    }
}
