package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.MongoServerException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.indexer.model.rebuild.Rebuild;
import org.ulpgc.tarantino.indexer.ports.Rebuilds;

import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.REQUESTED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SWITCHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TARGET_GENERATION;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.ifNull;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/**
 * “Open a rebuild” (SPEC §5.1). While one is {@code RUNNING} the filter does not match and the upsert fails with a
 * duplicate key, so a second request gets the running rebuild instead of an error (SPEC §5.4).
 */
public class MongoRebuilds implements Rebuilds {

    private static final int DUPLICATE_KEY = 11_000;

    private final MongoCollection<Document> indexState;
    private final IndexState pointer;

    public MongoRebuilds(MongoDatabase database) {
        this.indexState = database.getCollection(MongoCollections.INDEX_STATE);
        this.pointer = new IndexState(database);
    }

    @Override
    public Rebuild open() {
        int generation = pointer.currentGeneration();
        Bson notRunning = Filters.and(Filters.eq(ID, MongoCollections.REBUILD), Filters.ne(STATUS, RebuildStatus.RUNNING.name()));
        try {
            Document opened = indexState.findOneAndUpdate(notRunning, set(change(generation)),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            int target = opened.getInteger(TARGET_GENERATION);
            return new Rebuild(target - 1, target, RebuildStatus.RUNNING);
        } catch (MongoServerException e) {
            if (e.getCode() != DUPLICATE_KEY) {
                throw e;
            }
            return existing(generation);
        }
    }

    /**
     * Targets the generation after the last rebuild's target or, if there never was one, after {@code generation}. A
     * rebuild only finishes once the pointer is at its target, so both are the generation after the current one; reading
     * the old target in the same write means a switch between reading the pointer and writing cannot make the new
     * rebuild target the generation already being served.
     */
    private static Document change(int generation) {
        return new Document(TARGET_GENERATION, new Document("$add", List.of(ifNull(TARGET_GENERATION, generation), 1)))
                .append(STATUS, RebuildStatus.RUNNING.name())
                .append(REQUESTED_AT, NOW)
                .append(SWITCHED_AT, null);
    }

    private Rebuild existing(int generation) {
        Document rebuild = indexState.find(Filters.eq(ID, MongoCollections.REBUILD)).first();
        return new Rebuild(generation, rebuild.getInteger(TARGET_GENERATION), RebuildStatus.valueOf(rebuild.getString(STATUS)));
    }
}
