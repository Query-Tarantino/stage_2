package org.ulpgc.tarantino.common.operations;

/**
 * A lease that something keeps renewing while a job holds it (SPEC §5.1). Commands depend on it rather than on
 * {@link LeaseHeartbeat}, so their tests can lose the lease at a chosen step.
 */
public interface Lease extends AutoCloseable {

    /** Whether a renewal did not match: the job stops without writing anything else. */
    boolean lost();

    /** Stops renewing it. */
    @Override
    void close();
}
