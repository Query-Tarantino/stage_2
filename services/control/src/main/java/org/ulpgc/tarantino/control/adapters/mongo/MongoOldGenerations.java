package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.OldGenerations;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SWITCHED_AT;

/** The generations that step 6.3 of SPEC §5.3 drops once the current one has been served for the grace period. */
public class MongoOldGenerations implements OldGenerations {

    private final MongoDatabase database;
    private final Duration gracePeriod;

    public MongoOldGenerations(MongoDatabase database, Duration gracePeriod) {
        this.database = database;
        this.gracePeriod = gracePeriod;
    }

    @Override
    public OptionalInt settledCurrent() {
        Document pointer = database.getCollection(MongoCollections.INDEX_STATE)
                .find(Filters.and(Filters.eq(ID, MongoCollections.CURRENT), ServerTime.olderThan(SWITCHED_AT, gracePeriod)))
                .projection(Projections.include(GENERATION))
                .first();
        return pointer == null ? OptionalInt.empty() : OptionalInt.of(pointer.getInteger(GENERATION));
    }

    @Override
    public List<Integer> existingBefore(int generation) {
        return ExistingGenerations.all(database).stream()
                .filter(existing -> existing < generation)
                .toList();
    }

    @Override
    public void drop(int generation) {
        Stream.of(MongoCollections.metadata(generation), MongoCollections.postings(generation), MongoCollections.batches(generation))
                .forEach(collection -> database.getCollection(collection).drop());
    }
}
