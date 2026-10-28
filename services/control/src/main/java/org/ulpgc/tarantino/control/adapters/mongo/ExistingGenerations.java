package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.model.GenerationBatch;

import java.util.ArrayList;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;

/** The generations whose collections exist, found by their names (SPEC §4.3), so no step skips an old one. */
final class ExistingGenerations {

    private ExistingGenerations() {
    }

    /** The generations that have a {@code batches_g<k>} collection, ascending. */
    private static List<Integer> withBatches(MongoDatabase database) {
        List<String> collections = database.listCollectionNames().into(new ArrayList<>());
        return collections.stream()
                .flatMapToInt(collection -> MongoCollections.generationOf(collection).stream())
                .filter(generation -> collections.contains(MongoCollections.batches(generation)))
                .distinct()
                .sorted()
                .boxed()
                .toList();
    }

    /** The batches that match the filter in every {@code batches_g<k>}, by ascending generation. */
    static List<GenerationBatch> batchesMatching(MongoDatabase database, Bson filter) {
        return withBatches(database).stream()
                .flatMap(generation -> batchesMatching(database, generation, filter).stream())
                .toList();
    }

    private static List<GenerationBatch> batchesMatching(MongoDatabase database, int generation, Bson filter) {
        return database.getCollection(MongoCollections.batches(generation)).find(filter)
                .projection(Projections.include(ID))
                .map(batch -> new GenerationBatch(generation, batch.getObjectId(ID).toHexString()))
                .into(new ArrayList<>());
    }
}
