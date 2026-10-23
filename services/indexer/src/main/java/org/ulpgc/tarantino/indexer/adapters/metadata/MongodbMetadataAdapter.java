package org.ulpgc.tarantino.indexer.adapters.metadata;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoDatabases;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;

public class MongodbMetadataAdapter implements MetadataStorage {

    private static final String COLLECTION = "books";

    private final MongoCollection<Document> collection;

    public MongodbMetadataAdapter(String connectionUri) {
        this.collection = MongoDatabases.database(connectionUri).getCollection(COLLECTION);
        this.collection.createIndex(Indexes.ascending("book_id"), new IndexOptions().unique(true));
    }

    @Override
    public void save(Book book) {
        collection.replaceOne(Filters.eq("book_id", book.bookId()), document(book), new ReplaceOptions().upsert(true));
    }

    private static Document document(Book book) {
        return new Document("book_id", book.bookId())
                .append("title", book.title())
                .append("author", book.author())
                .append("language", book.language())
                .append("path", PortablePaths.of(book.path()));
    }

}
