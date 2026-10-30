package org.ulpgc.tarantino.control.model;

/** An {@code INDEXING} book with the batch its {@code batch_id} and {@code generation} point to (SPEC §4.3). */
public record IndexingBook(int bookId, GenerationBatch batch) {
}
