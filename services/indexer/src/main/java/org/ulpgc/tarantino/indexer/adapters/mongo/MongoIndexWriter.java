package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;
import org.ulpgc.tarantino.indexer.ports.IndexWriter;

import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR_TERMS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LANGUAGES;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LANGUAGE_NAMES;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ORIGINAL_YEAR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TERM;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TFS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TITLE;
import static org.ulpgc.tarantino.common.mongo.MongoFields.YEAR;

/** Writes a batch's {@code metadata_g<n>} and {@code postings_g<n>} documents with upserts (SPEC §4.3). */
public class MongoIndexWriter implements IndexWriter {

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);
    private static final BulkWriteOptions UNORDERED = new BulkWriteOptions().ordered(false);

    private final MongoDatabase database;

    public MongoIndexWriter(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public void writeMetadata(int generation, List<BookMetadata> books) {
        write(database.getCollection(MongoCollections.metadata(generation)), books.stream()
                .<WriteModel<Document>>map(book -> new ReplaceOneModel<>(Filters.eq(ID, book.bookId()), document(book), UPSERT))
                .toList());
    }

    @Override
    public void writePostings(Batch batch, List<TermPostings> postings) {
        ObjectId batchId = new ObjectId(batch.batchId());
        write(database.getCollection(MongoCollections.postings(batch.generation())), postings.stream()
                .<WriteModel<Document>>map(posting -> new ReplaceOneModel<>(
                        Filters.and(Filters.eq(TERM, posting.term()), Filters.eq(BATCH_ID, batchId)), document(posting, batchId), UPSERT))
                .toList());
    }

    private static void write(MongoCollection<Document> collection, List<WriteModel<Document>> writes) {
        if (!writes.isEmpty()) {
            collection.bulkWrite(writes, UNORDERED);
        }
    }

    private static Document document(BookMetadata book) {
        return new Document(ID, book.bookId())
                .append(TITLE, book.title())
                .append(AUTHOR, book.author())
                .append(AUTHOR_TERMS, book.authorTerms())
                .append(LANGUAGES, book.languages())
                .append(LANGUAGE_NAMES, book.languageNames())
                .append(YEAR, book.year())
                .append(ORIGINAL_YEAR, book.originalYear());
    }

    private static Document document(TermPostings posting, ObjectId batchId) {
        return new Document(TERM, posting.term())
                .append(BATCH_ID, batchId)
                .append(BOOK_IDS, posting.bookIds())
                .append(TFS, posting.tfs());
    }
}
