package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.Rebuilds;

import java.util.OptionalInt;

import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SWITCHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TARGET_GENERATION;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/** “Switch generation” and “Finish the rebuild” (SPEC §5.1) with the condition of SPEC §5.4, step 6.2 of SPEC §5.3. */
public class MongoRebuilds implements Rebuilds {

    private final IndexState indexState;
    private final MongoCollection<Document> pointers;
    private final MongoCollection<Document> states;

    public MongoRebuilds(MongoDatabase database) {
        this.indexState = new IndexState(database);
        this.pointers = database.getCollection(MongoCollections.INDEX_STATE);
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public OptionalInt runningTarget() {
        Document rebuild = pointers.find(running()).projection(Projections.include(TARGET_GENERATION)).first();
        return rebuild == null ? OptionalInt.empty() : OptionalInt.of(rebuild.getInteger(TARGET_GENERATION));
    }

    @Override
    public int currentGeneration() {
        return indexState.currentGeneration();
    }

    @Override
    public boolean readyToSwitch(int target) {
        return states.find(BookStates.blockingSwitchTo(target))
                .projection(Projections.include(ID))
                .first() == null;
    }

    @Override
    public boolean switchTo(int target) {
        Bson previousGeneration = Filters.and(Filters.eq(ID, MongoCollections.CURRENT), Filters.eq(GENERATION, target - 1));
        Document switched = new Document(GENERATION, target).append(SWITCHED_AT, NOW);
        return pointers.updateOne(previousGeneration, set(switched)).getModifiedCount() == 1;
    }

    @Override
    public boolean finish(int target) {
        Bson runningIntoTarget = Filters.and(running(), Filters.eq(TARGET_GENERATION, target));
        Document done = new Document(STATUS, RebuildStatus.DONE.name()).append(SWITCHED_AT, NOW);
        return pointers.updateOne(runningIntoTarget, set(done)).getModifiedCount() == 1;
    }

    private static Bson running() {
        return Filters.and(Filters.eq(ID, MongoCollections.REBUILD), Filters.eq(STATUS, RebuildStatus.RUNNING.name()));
    }
}
