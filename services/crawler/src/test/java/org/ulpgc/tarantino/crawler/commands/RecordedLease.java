package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.common.operations.Lease;

import java.util.function.BooleanSupplier;

/** A lease that is never lost and remembers its renewal and whether the job closed it. */
final class RecordedLease implements Lease {

    private BooleanSupplier renewal;
    private int started;
    private boolean closed;

    Lease start(BooleanSupplier renewal) {
        this.renewal = renewal;
        started++;
        return this;
    }

    boolean renew() {
        return renewal.getAsBoolean();
    }

    int started() {
        return started;
    }

    boolean closed() {
        return closed;
    }

    @Override
    public boolean lost() {
        return false;
    }

    @Override
    public void close() {
        closed = true;
    }
}
