package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.indexer.model.rebuild.IndexStatus;
import org.ulpgc.tarantino.indexer.model.rebuild.RebuildProgress;
import org.ulpgc.tarantino.indexer.ports.IndexStatusReader;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.PUBLISHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TARGET_GENERATION;

/**
 * {@code GET /index/status} from Mongo (SPEC §6.5): the published batches of the current generation, the size of its
 * three collections from {@code $collStats} and, during a rebuild, the books that block its switch (SPEC §5.4).
 */
public class MongoIndexStatus implements IndexStatusReader {

    private static final String BOOKS = "books";
    private static final String BATCHES = "batches";
    private static final String LAST_UPDATE = "last_update";
    private static final String STORAGE_STATS = "storageStats";
    private static final String STORAGE_SIZE = "storageSize";
    private static final String TOTAL_INDEX_SIZE = "totalIndexSize";
    private static final double BYTES_PER_MEBIBYTE = 1024 * 1024;
    private static final double TENTHS = 10;

    private final MongoDatabase database;
    private final IndexState pointer;

    public MongoIndexStatus(MongoDatabase database) {
        this.database = database;
        this.pointer = new IndexState(database);
    }

    @Override
    public IndexStatus status() {
        int generation = pointer.currentGeneration();
        Optional<Document> published = publishedBatches(generation);
        return new IndexStatus(generation,
                published.map(batches -> batches.get(BOOKS, Number.class).longValue()).orElse(0L),
                published.map(batches -> batches.get(BATCHES, Number.class).longValue()).orElse(0L),
                published.map(batches -> batches.getDate(LAST_UPDATE)).map(Date::toInstant).orElse(null),
                mebibytes(storageBytes(generation)),
                runningRebuild().orElse(null));
    }

    /** {@code bytes} in MiB with one decimal, as {@code index_size_mb} goes. */
    static double mebibytes(long bytes) {
        return Math.round(bytes / BYTES_PER_MEBIBYTE * TENTHS) / TENTHS;
    }

    private Optional<Document> publishedBatches(int generation) {
        return Optional.ofNullable(database.getCollection(MongoCollections.batches(generation)).aggregate(List.of(
                Aggregates.match(Filters.eq(STATUS, BatchStatus.PUBLISHED.name())),
                Aggregates.group(null,
                        Accumulators.sum(BOOKS, new Document("$size", "$" + BOOK_IDS)),
                        Accumulators.sum(BATCHES, 1),
                        Accumulators.max(LAST_UPDATE, "$" + PUBLISHED_AT)))).first());
    }

    private long storageBytes(int generation) {
        Set<String> existing = database.listCollectionNames().into(new HashSet<>());
        return Stream.of(MongoCollections.metadata(generation), MongoCollections.postings(generation), MongoCollections.batches(generation))
                .filter(existing::contains)
                .mapToLong(this::storageBytes)
                .sum();
    }

    private long storageBytes(String collection) {
        Document stats = database.getCollection(collection)
                .aggregate(List.of(new Document("$collStats", new Document(STORAGE_STATS, new Document()))))
                .first()
                .get(STORAGE_STATS, Document.class);
        return stats.get(STORAGE_SIZE, Number.class).longValue() + stats.get(TOTAL_INDEX_SIZE, Number.class).longValue();
    }

    private Optional<RebuildProgress> runningRebuild() {
        MongoCollection<Document> indexState = database.getCollection(MongoCollections.INDEX_STATE);
        return Optional.ofNullable(indexState.find(Filters.eq(ID, MongoCollections.REBUILD)).first())
                .filter(rebuild -> RebuildStatus.RUNNING.name().equals(rebuild.getString(STATUS)))
                .map(rebuild -> rebuild.getInteger(TARGET_GENERATION))
                .map(target -> new RebuildProgress(target, RebuildStatus.RUNNING, database.getCollection(MongoCollections.BOOK_STATES)
                        .countDocuments(BookStates.blockingSwitchTo(target))));
    }
}
