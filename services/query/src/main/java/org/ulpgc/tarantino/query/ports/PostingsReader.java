package org.ulpgc.tarantino.query.ports;

import org.ulpgc.tarantino.query.model.TermPostings;

import java.util.Collection;
import java.util.List;

public interface PostingsReader {

    /** The postings of the terms written by the given batches, with {@code {term: {$in}, batch_id: {$in}}} (SPEC §7). */
    List<TermPostings> postings(int generation, Collection<String> terms, Collection<String> batchIds);
}
