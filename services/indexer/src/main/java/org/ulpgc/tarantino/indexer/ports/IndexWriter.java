package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;

import java.util.List;

/** What a batch writes into its generation while it is {@code WRITING}, invisible until it is published. */
public interface IndexWriter {

    void writeMetadata(int generation, List<BookMetadata> books);

    void writePostings(Batch batch, List<TermPostings> postings);
}
