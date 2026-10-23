package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR_TERMS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LANGUAGES;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LEASE_UNTIL;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TERM;
import static org.ulpgc.tarantino.common.mongo.MongoFields.YEAR;

/** The indexes of SPEC §4.4, exactly those. {@code createIndexes} is idempotent, so every service can ensure them. */
public final class MongoIndexes {

    private MongoIndexes() {
    }

    /** The {@code book_states} indexes; {@code index_state} only needs its {@code _id}. */
    public static void ensureStateIndexes(MongoDatabase database) {
        database.getCollection(MongoCollections.BOOK_STATES).createIndexes(List.of(
                new IndexModel(Indexes.ascending(STATUS, ENQUEUED_AT)),
                new IndexModel(Indexes.ascending(STATUS, LEASE_UNTIL)),
                new IndexModel(Indexes.ascending(BATCH_ID)),
                new IndexModel(Indexes.ascending(GENERATION, STATUS))));
    }

    /** The indexes of the three collections of a generation, before the first write to it. */
    public static void ensureGenerationIndexes(MongoDatabase database, int generation) {
        database.getCollection(MongoCollections.metadata(generation)).createIndexes(List.of(
                new IndexModel(Indexes.ascending(AUTHOR_TERMS)),
                new IndexModel(Indexes.ascending(LANGUAGES, YEAR)),
                new IndexModel(Indexes.ascending(YEAR))));
        database.getCollection(MongoCollections.postings(generation)).createIndexes(List.of(
                new IndexModel(Indexes.ascending(TERM, BATCH_ID), new IndexOptions().unique(true)),
                new IndexModel(Indexes.ascending(BATCH_ID))));
        database.getCollection(MongoCollections.batches(generation)).createIndexes(List.of(
                new IndexModel(Indexes.ascending(STATUS, LEASE_UNTIL))));
    }
}
