package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.indexer.model.batch.Batch;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The indexer's writes on {@code batches_g<n>} and {@code book_states} (SPEC §5.1), each a single conditional write. */
public interface BatchStore {

    /** The target of a {@code RUNNING} rebuild, or the current generation (SPEC §5.4). */
    int writeGeneration();

    /** “Open a batch”, after ensuring the indexes of its generation. */
    Batch open(int generation);

    /** “Claim its books”: the books that are now {@code INDEXING} in the batch, ascending. */
    List<Integer> claim(Batch batch, Collection<Integer> bookIds);

    /** “Renew a batch”: false if the lease was lost, because the batch is no longer {@code WRITING} with its {@code claim_id}. */
    boolean renew(Batch batch);

    /** “Fail at indexing” with {@code NOT_IN_DATALAKE}: false if the book is no longer in the batch. */
    boolean failAtIndexing(Batch batch, int bookId);

    /** “Publish a batch”: its {@code published_at}, or empty if its lease was lost. */
    Optional<Instant> publish(Batch batch, List<Integer> bookIds);

    /** Marks the batch's {@code INDEXING} books {@code PUBLISHED}, the second write of “Publish a batch”. */
    void markPublished(Batch batch);
}
