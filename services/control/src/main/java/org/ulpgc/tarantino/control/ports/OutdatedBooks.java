package org.ulpgc.tarantino.control.ports;

import java.util.List;

public interface OutdatedBooks {

    /** The target of a {@code RUNNING} rebuild, and {@code current.generation} otherwise (SPEC §5.4). */
    int writeGeneration();

    /** The {@code PUBLISHED} books whose {@code generation} is lower than the write generation (SPEC §5.3, step 6.1). */
    List<Integer> outdated(int writeGeneration);

    /** “Reindex into another generation” (SPEC §5.1): whether it matched, false if it was already reindexed. */
    boolean reindex(int bookId, int writeGeneration);
}
