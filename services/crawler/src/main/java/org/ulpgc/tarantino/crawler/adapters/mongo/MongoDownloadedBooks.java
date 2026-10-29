package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.crawler.ports.DownloadedBooks;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/** Reads the downloaded books of {@code GET /ingest/list} from {@code book_states} (SPEC §6.5). */
public class MongoDownloadedBooks implements DownloadedBooks {

    private static final List<String> DOWNLOADED = Stream.of(BookStatus.DOWNLOADED, BookStatus.INDEXING, BookStatus.PUBLISHED)
            .map(BookStatus::name)
            .toList();

    private final MongoCollection<Document> states;

    public MongoDownloadedBooks(MongoDatabase database) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public List<Integer> downloaded() {
        return states.find(Filters.in(STATUS, DOWNLOADED))
                .projection(Projections.include(ID))
                .sort(Sorts.ascending(ID))
                .map(state -> state.getInteger(ID))
                .into(new ArrayList<>());
    }
}
