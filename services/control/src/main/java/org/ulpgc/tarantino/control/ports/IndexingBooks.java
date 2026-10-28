package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.model.IndexingBook;

import java.util.List;
import java.util.Optional;

public interface IndexingBooks {

    /** The {@code INDEXING} books with their batch (SPEC §5.3, step 3). */
    List<IndexingBook> indexing();

    /** The state of the batch in {@code batches_g<generation>}, or empty if it has no document. */
    Optional<BatchStatus> statusOf(GenerationBatch batch);

    /** “Release its books” (SPEC §5.1) for one book: whether it matched, false if someone changed it first. */
    boolean release(IndexingBook book);

    /** “Complete a publication” (SPEC §5.1) for one book: whether it matched, false if it was already done. */
    boolean complete(IndexingBook book);
}
