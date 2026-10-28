package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import org.ulpgc.tarantino.common.mongo.MongoCollections;

import java.util.ArrayList;
import java.util.List;

/** The generations whose collections exist, found by their names (SPEC §4.3), so no step skips an old one. */
final class ExistingGenerations {

    private ExistingGenerations() {
    }

    /** The generations that have a {@code batches_g<k>} collection, ascending. */
    static List<Integer> withBatches(MongoDatabase database) {
        List<String> collections = database.listCollectionNames().into(new ArrayList<>());
        return collections.stream()
                .flatMapToInt(collection -> MongoCollections.generationOf(collection).stream())
                .filter(generation -> collections.contains(MongoCollections.batches(generation)))
                .distinct()
                .sorted()
                .boxed()
                .toList();
    }
}
