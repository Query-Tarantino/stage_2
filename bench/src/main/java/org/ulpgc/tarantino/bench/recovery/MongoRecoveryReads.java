package org.ulpgc.tarantino.bench.recovery;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TERM;

/** What {@code bench recovery} reads straight from MongoDB (SPEC §8). */
public class MongoRecoveryReads implements RecoveryReads {

    private static final String BATCHES = "batches";

    private final MongoDatabase database;
    private final IndexState indexState;

    public MongoRecoveryReads(MongoDatabase database) {
        this.database = database;
        this.indexState = new IndexState(database);
    }

    @Override
    public List<Integer> seededBooks() {
        return database.getCollection(MongoCollections.BOOK_STATES).find()
                .sort(Sorts.ascending(ID))
                .map(state -> state.getInteger(ID))
                .into(new ArrayList<>());
    }

    @Override
    public List<Integer> booksInTwoPublishedBatches() {
        return batches().aggregate(List.of(
                        Aggregates.match(Filters.eq(STATUS, BatchStatus.PUBLISHED.name())),
                        Aggregates.unwind("$" + BOOK_IDS),
                        Aggregates.group("$" + BOOK_IDS, Accumulators.sum(BATCHES, 1)),
                        Aggregates.match(Filters.gt(BATCHES, 1)),
                        Aggregates.sort(Sorts.ascending(ID))))
                .map(book -> book.getInteger(ID))
                .into(new ArrayList<>());
    }

    @Override
    public List<String> termsInText(int bookId, List<String> terms) {
        Set<String> inText = database.getCollection(MongoCollections.postings(indexState.currentGeneration()))
                .distinct(TERM, Filters.and(Filters.in(TERM, terms), Filters.eq(BOOK_IDS, bookId)), String.class)
                .into(new HashSet<>());
        return terms.stream().filter(inText::contains).toList();
    }

    @Override
    public long postingsOfAbandonedBatches() {
        List<ObjectId> abandoned = batches().distinct(ID, Filters.eq(STATUS, BatchStatus.ABANDONED.name()), ObjectId.class)
                .into(new ArrayList<>());
        return abandoned.isEmpty() ? 0 : database.getCollection(MongoCollections.postings(indexState.currentGeneration()))
                .countDocuments(Filters.in(BATCH_ID, abandoned));
    }

    private MongoCollection<Document> batches() {
        return database.getCollection(MongoCollections.batches(indexState.currentGeneration()));
    }
}
