package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.indexer.adapters.index.mongo.MongodbIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.metadata.MongodbMetadataAdapter;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MongodbAdaptersTest {

    @RegisterExtension
    static final TemporaryMongoDatabase MONGO = new TemporaryMongoDatabase();

    @Test
    void indexAddsUniquePostingsPerTerm() {
        MongodbIndexAdapter index = new MongodbIndexAdapter(MONGO.uri());
        index.add(new TermOccurrences(1342, Map.of("island", 2, "whale", 1)));
        index.add(new TermOccurrences(5, Map.of("island", 1)));
        index.flush();
        index.add(new TermOccurrences(5, Map.of("island", 1)));
        index.flush();

        assertEquals(List.of(5, 1342), postings("island"));
        assertEquals(List.of(1342), postings("whale"));
    }

    @Test
    void flushWithoutPendingTermsWritesNothing() {
        new MongodbIndexAdapter(MONGO.uri()).flush();

        assertEquals(0, database().getCollection("inverted_index").countDocuments());
    }

    @Test
    void metadataIsUpsertedByBookId() {
        MongodbMetadataAdapter metadata = new MongodbMetadataAdapter(MONGO.uri());
        metadata.save(new Book(5, "Old title", null, "English", Path.of("datalake", "5", "body.txt")));
        metadata.save(new Book(5, "Robinson Crusoe", null, "English", Path.of("datalake", "5", "body.txt")));

        Document book = Objects.requireNonNull(database().getCollection("books").find(Filters.eq("book_id", 5)).first());
        assertEquals(1, database().getCollection("books").countDocuments());
        assertEquals("Robinson Crusoe", book.getString("title"));
        assertNull(book.getString("author"));
        assertEquals("datalake/5/body.txt", book.getString("path"));
    }

    private List<Integer> postings(String term) {
        Document document = Objects.requireNonNull(database().getCollection("inverted_index").find(Filters.eq("term", term)).first());
        return document.getList("postings", Integer.class).stream().sorted().toList();
    }

    private static MongoDatabase database() {
        return MongoDatabases.database(MONGO.uri());
    }
}
