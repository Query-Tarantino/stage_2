package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.indexer.adapters.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.query.adapters.index.mongo.MongodbIndexReader;
import org.ulpgc.tarantino.query.adapters.metadata.MongodbMetadataReader;
import org.ulpgc.tarantino.query.model.BookMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongodbReadersTest {

    @RegisterExtension
    static final TemporaryMongoDatabase MONGO = new TemporaryMongoDatabase();

    @BeforeEach
    void insertDocuments() {
        database().getCollection("inverted_index").insertOne(new Document("term", "island").append("postings", List.of(1342, 5)));
        database().getCollection("books").insertMany(List.of(
                book(76, "Huckleberry Finn", "Mark Twain"),
                book(74, "Tom Sawyer", "Mark Twain"),
                book(84, "Frankenstein", null)));
    }

    @Test
    void readsPostingsOfATermAndNothingForUnknownTerms() {
        MongodbIndexReader index = new MongodbIndexReader(MONGO.uri());

        assertEquals(Set.of(5, 1342), index.postings("island"));
        assertEquals(Set.of(), index.postings("whale"));
    }

    @Test
    void findsABookByIdKeepingMissingFieldsAsNull() {
        assertEquals(Optional.of(new BookMetadata(84, "Frankenstein", null, "English", Path.of("datalake/84/body.txt"))),
                new MongodbMetadataReader(MONGO.uri()).book(84));
    }

    @Test
    void findsBooksByCaseInsensitiveAuthorSubstringOrderedById() {
        List<Integer> ids = new MongodbMetadataReader(MONGO.uri()).booksBy("twain").stream()
                .map(BookMetadata::bookId)
                .toList();

        assertEquals(List.of(74, 76), ids);
    }

    private static Document book(int bookId, String title, String author) {
        return new Document("book_id", bookId).append("title", title).append("author", author)
                .append("language", "English").append("path", "datalake/" + bookId + "/body.txt");
    }

    private static MongoDatabase database() {
        return MongoDatabases.database(MONGO.uri());
    }
}
