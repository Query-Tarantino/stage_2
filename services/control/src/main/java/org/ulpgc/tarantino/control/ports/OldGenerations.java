package org.ulpgc.tarantino.control.ports;

import java.util.List;
import java.util.OptionalInt;

/** The generations below the current one whose collections step 6.3 of the reconciliation drops (SPEC §5.3). */
public interface OldGenerations {

    /** {@code current.generation} once its {@code switched_at} is older than {@code $$NOW − TARANTINO_GC_SECONDS}. */
    OptionalInt settledCurrent();

    /** The generations below this one that still have any of their collections, ascending. */
    List<Integer> existingBefore(int generation);

    /** Drops {@code metadata_g<k>}, {@code postings_g<k>} and {@code batches_g<k>} (SPEC §5.3, step 6.3). */
    void drop(int generation);
}
