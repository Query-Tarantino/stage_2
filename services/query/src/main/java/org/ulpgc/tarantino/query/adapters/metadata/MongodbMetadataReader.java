package org.ulpgc.tarantino.query.adapters.metadata;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.ulpgc.tarantino.query.adapters.mongo.MongoDatabases;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class MongodbMetadataReader implements MetadataReader {

    private static final String COLLECTION = "books";

    private final MongoCollection<Document> collection;

    public MongodbMetadataReader(String connectionUri) {
        this.collection = MongoDatabases.database(connectionUri).getCollection(COLLECTION);
    }

    @Override
    public Optional<BookMetadata> book(int bookId) {
        return Optional.ofNullable(collection.find(Filters.eq("book_id", bookId)).first()).map(MongodbMetadataReader::bookOf);
    }

    @Override
    public List<BookMetadata> booksBy(String author) {
        return collection.find(Filters.regex("author", Pattern.quote(author), "i"))
                .sort(Sorts.ascending("book_id"))
                .map(MongodbMetadataReader::bookOf)
                .into(new ArrayList<>());
    }

    private static BookMetadata bookOf(Document document) {
        return new BookMetadata(document.getInteger("book_id"), document.getString("title"), document.getString("author"),
                document.getString("language"), Path.of(document.getString("path")));
    }
}
