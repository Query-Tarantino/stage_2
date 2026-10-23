package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.TermPostings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoReadersTest {

    private static final ObjectId PUBLISHED = new ObjectId();
    private static final ObjectId WRITING = new ObjectId();

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @BeforeEach
    void storeOnePublishedAndOneWritingBatch() {
        database().getCollection(MongoCollections.batches(1)).insertMany(List.of(
                new Document(MongoFields.ID, PUBLISHED).append(MongoFields.STATUS, BatchStatus.PUBLISHED.name())
                        .append(MongoFields.BOOK_IDS, List.of(84, 1342)),
                new Document(MongoFields.ID, WRITING).append(MongoFields.STATUS, BatchStatus.WRITING.name())
                        .append(MongoFields.BOOK_IDS, List.of())));
        database().getCollection(MongoCollections.postings(1)).insertMany(List.of(
                posting("pride", PUBLISHED, List.of(84, 1342), List.of(6, 55)),
                posting("pride", WRITING, List.of(2600), List.of(30)),
                posting("whale", PUBLISHED, List.of(84), List.of(1))));
    }

    @Test
    void listsOnlyThePublishedBatchesAndTheirBooks() {
        MongoPublishedBatches batches = new MongoPublishedBatches(database());

        assertEquals(List.of(PUBLISHED.toHexString()), batches.publishedBatchIds(1));
        assertEquals(Map.of(PUBLISHED.toHexString(), List.of(84, 1342)),
                batches.bookIds(1, List.of(PUBLISHED.toHexString(), WRITING.toHexString())));
    }

    @Test
    void readsThePostingsOfTheTermsWrittenByTheGivenBatches() {
        List<TermPostings> postings = new MongoPostingsReader(database()).postings(1, List.of("pride"), Set.of(PUBLISHED.toHexString()));

        assertEquals(List.of(new TermPostings("pride", List.of(84, 1342), List.of(6, 55))), postings);
    }

    @Test
    void answersTheFirstLanguageOfABookOrNull() {
        database().getCollection(MongoCollections.metadata(1)).insertMany(List.of(
                book(417, "Deutsche Geistliche Lieder", "de", "en"),
                book(1342, "Pride and Prejudice"),
                book(84, "Frankenstein", "en")));

        List<SearchResult> results = new ArrayList<>(new MongoMetadataReader(database()).results(1, List.of(417, 1342)));
        results.sort((first, second) -> Integer.compare(first.bookId(), second.bookId()));

        assertEquals(List.of(new SearchResult(417, "Deutsche Geistliche Lieder", null, "de", 1996),
                new SearchResult(1342, "Pride and Prejudice", null, null, 1996)), results);
    }

    private static Document posting(String term, ObjectId batchId, List<Integer> bookIds, List<Integer> tfs) {
        return new Document(MongoFields.TERM, term).append(MongoFields.BATCH_ID, batchId)
                .append(MongoFields.BOOK_IDS, bookIds).append(MongoFields.TFS, tfs);
    }

    private static Document book(int bookId, String title, String... languages) {
        return new Document(MongoFields.ID, bookId).append(MongoFields.TITLE, title).append(MongoFields.AUTHOR, null)
                .append(MongoFields.LANGUAGES, Arrays.asList(languages)).append(MongoFields.YEAR, 1996);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
