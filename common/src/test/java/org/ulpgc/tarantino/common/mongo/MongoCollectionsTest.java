package org.ulpgc.tarantino.common.mongo;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoCollectionsTest {

    @Test
    void namesTheCollectionsOfAGeneration() {
        assertEquals("metadata_g2", MongoCollections.metadata(2));
        assertEquals("postings_g1", MongoCollections.postings(1));
        assertEquals("batches_g12", MongoCollections.batches(12));
    }

    @Test
    void readsTheGenerationBackFromItsCollectionsOnly() {
        assertEquals(OptionalInt.of(12), MongoCollections.generationOf("postings_g12"));
        assertEquals(OptionalInt.of(1), MongoCollections.generationOf("batches_g1"));
        assertEquals(OptionalInt.empty(), MongoCollections.generationOf("book_states"));
        assertEquals(OptionalInt.empty(), MongoCollections.generationOf("postings_g0"));
        assertEquals(OptionalInt.empty(), MongoCollections.generationOf("old_postings_g1"));
    }
}
