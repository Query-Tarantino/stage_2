package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.OutdatedBooks;

import java.util.ArrayList;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** “Reindex into another generation” (SPEC §5.1) over the books of step 6.1 of SPEC §5.3. */
public class MongoOutdatedBooks implements OutdatedBooks {

    private final IndexState indexState;
    private final MongoCollection<Document> states;

    public MongoOutdatedBooks(MongoDatabase database) {
        this.indexState = new IndexState(database);
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public int writeGeneration() {
        return indexState.writeGeneration();
    }

    @Override
    public List<Integer> outdated(int writeGeneration) {
        return states.find(publishedBefore(writeGeneration))
                .projection(Projections.include(ID))
                .map(state -> state.getInteger(ID))
                .into(new ArrayList<>());
    }

    @Override
    public boolean reindex(int bookId, int writeGeneration) {
        Document downloaded = new Document(STATUS, BookStatus.DOWNLOADED.name())
                .append(ENQUEUED_AT, NOW)
                .append(UPDATED_AT, NOW);
        Bson outdatedBook = Filters.and(Filters.eq(ID, bookId), publishedBefore(writeGeneration));
        return states.updateOne(outdatedBook, setThenUnset(downloaded, BATCH_ID)).getModifiedCount() == 1;
    }

    private static Bson publishedBefore(int writeGeneration) {
        return Filters.and(Filters.eq(STATUS, BookStatus.PUBLISHED.name()), Filters.lt(GENERATION, writeGeneration));
    }
}
