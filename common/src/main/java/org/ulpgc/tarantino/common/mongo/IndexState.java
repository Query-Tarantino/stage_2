package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.RebuildStatus;

import java.util.Optional;

import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SWITCHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TARGET_GENERATION;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.ifNull;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

public class IndexState {

    private static final int FIRST_GENERATION = 1;

    private final MongoCollection<Document> collection;

    public IndexState(MongoDatabase database) {
        this.collection = database.getCollection(MongoCollections.INDEX_STATE);
    }

    /** “Create the pointer” (SPEC §5.1): every service runs it at startup, and only the first one creates it. */
    public void createPointer() {
        collection.updateOne(Filters.eq(ID, MongoCollections.CURRENT),
                set(new Document(GENERATION, ifNull(GENERATION, FIRST_GENERATION)).append(SWITCHED_AT, ifNull(SWITCHED_AT, NOW))),
                new UpdateOptions().upsert(true));
    }

    /** {@code current.generation}: the generation that {@code query} serves (SPEC §4.3). */
    public int currentGeneration() {
        return document(MongoCollections.CURRENT)
                .map(pointer -> pointer.getInteger(GENERATION))
                .orElseThrow(() -> new IllegalStateException("There is no generation pointer yet"));
    }

    /** The generation to write: the target of a {@code RUNNING} rebuild, and the current one otherwise (SPEC §5.4). */
    public int writeGeneration() {
        return document(MongoCollections.REBUILD)
                .filter(rebuild -> RebuildStatus.RUNNING.name().equals(rebuild.getString(STATUS)))
                .map(rebuild -> rebuild.getInteger(TARGET_GENERATION))
                .orElseGet(this::currentGeneration);
    }

    private Optional<Document> document(String id) {
        return Optional.ofNullable(collection.find(Filters.eq(ID, id)).first());
    }
}
