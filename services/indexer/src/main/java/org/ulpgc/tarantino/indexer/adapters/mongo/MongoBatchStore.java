package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.ports.BatchStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.CLAIM_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LAST_ERROR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LEASE_UNTIL;
import static org.ulpgc.tarantino.common.mongo.MongoFields.OPENED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.OWNER;
import static org.ulpgc.tarantino.common.mongo.MongoFields.PUBLISHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** The indexer's conditional writes of SPEC §5.1 on {@code batches_g<n>} and {@code book_states}. */
public class MongoBatchStore implements BatchStore {

    private static final String LITERAL = "$literal";

    private final MongoDatabase database;
    private final MongoCollection<Document> states;
    private final IndexState indexState;
    private final Duration lease;
    private final String owner;
    private final Set<Integer> indexedGenerations = ConcurrentHashMap.newKeySet();

    public MongoBatchStore(MongoDatabase database, Duration lease, String owner) {
        this.database = database;
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
        this.indexState = new IndexState(database);
        this.lease = lease;
        this.owner = owner;
    }

    @Override
    public int writeGeneration() {
        return indexState.writeGeneration();
    }

    @Override
    public Batch open(int generation) {
        if (indexedGenerations.add(generation)) {
            MongoIndexes.ensureGenerationIndexes(database, generation);
        }
        Batch batch = new Batch(new ObjectId().toHexString(), UUID.randomUUID().toString(), generation);
        Document change = new Document(STATUS, BatchStatus.WRITING.name())
                .append(CLAIM_ID, batch.claimId())
                .append(OWNER, owner)
                .append(LEASE_UNTIL, nowPlus(lease))
                .append(BOOK_IDS, new Document(LITERAL, List.of()))
                .append(OPENED_AT, NOW);
        batches(batch).updateOne(Filters.eq(ID, id(batch)), set(change), new UpdateOptions().upsert(true));
        return batch;
    }

    @Override
    public List<Integer> claim(Batch batch, Collection<Integer> bookIds) {
        Document change = new Document(STATUS, BookStatus.INDEXING.name())
                .append(BATCH_ID, id(batch))
                .append(GENERATION, batch.generation())
                .append(UPDATED_AT, NOW);
        states.updateMany(Filters.and(Filters.in(ID, bookIds), Filters.eq(STATUS, BookStatus.DOWNLOADED.name())), set(change));
        return states.find(indexingIn(batch))
                .projection(Projections.include(ID))
                .sort(Sorts.ascending(ID))
                .map(state -> state.getInteger(ID))
                .into(new ArrayList<>());
    }

    @Override
    public boolean failAtIndexing(Batch batch, int bookId) {
        Document change = new Document(STATUS, BookStatus.FAILED.name())
                .append(LAST_ERROR, BookErrorCode.NOT_IN_DATALAKE.name())
                .append(UPDATED_AT, NOW);
        return states.updateOne(Filters.and(Filters.eq(ID, bookId), indexingIn(batch)), setThenUnset(change, BATCH_ID))
                .getMatchedCount() == 1;
    }

    @Override
    public Optional<Instant> publish(Batch batch, List<Integer> bookIds) {
        Document change = new Document(STATUS, BatchStatus.PUBLISHED.name())
                .append(PUBLISHED_AT, NOW)
                .append(BOOK_IDS, new Document(LITERAL, bookIds));
        Bson writingWithLease = Filters.and(Filters.eq(ID, id(batch)), Filters.eq(STATUS, BatchStatus.WRITING.name()),
                Filters.eq(CLAIM_ID, batch.claimId()), Filters.expr(new Document("$gt", List.of("$" + LEASE_UNTIL, NOW))));
        return Optional.ofNullable(batches(batch).findOneAndUpdate(writingWithLease, setThenUnset(change, LEASE_UNTIL),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(published -> published.getDate(PUBLISHED_AT).toInstant());
    }

    @Override
    public void markPublished(Batch batch) {
        Document change = new Document(STATUS, BookStatus.PUBLISHED.name()).append(PUBLISHED_AT, NOW).append(UPDATED_AT, NOW);
        states.updateMany(indexingIn(batch), set(change));
    }

    private MongoCollection<Document> batches(Batch batch) {
        return database.getCollection(MongoCollections.batches(batch.generation()));
    }

    private static Bson indexingIn(Batch batch) {
        return Filters.and(Filters.eq(BATCH_ID, id(batch)), Filters.eq(STATUS, BookStatus.INDEXING.name()));
    }

    private static ObjectId id(Batch batch) {
        return new ObjectId(batch.batchId());
    }
}
