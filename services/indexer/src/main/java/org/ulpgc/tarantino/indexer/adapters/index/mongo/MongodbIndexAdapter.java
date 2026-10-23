package org.ulpgc.tarantino.indexer.adapters.index.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.ulpgc.tarantino.indexer.adapters.index.PendingPostings;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoDatabases;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MongodbIndexAdapter implements InvertedIndexStorage {

    private static final String COLLECTION = "inverted_index";

    private final MongoCollection<Document> collection;
    private final PendingPostings pending = new PendingPostings();

    public MongodbIndexAdapter(String connectionUri) {
        this.collection = MongoDatabases.database(connectionUri).getCollection(COLLECTION);
        this.collection.createIndex(Indexes.ascending("term"), new IndexOptions().unique(true));
    }

    @Override
    public void add(TermOccurrences occurrences) {
        pending.add(occurrences);
    }

    @Override
    public void flush() {
        List<WriteModel<Document>> updates = updates(pending.drain());
        if (!updates.isEmpty()) {
            collection.bulkWrite(updates, new BulkWriteOptions().ordered(false));
        }
    }

    private static List<WriteModel<Document>> updates(Map<String, Set<Integer>> postings) {
        return postings.entrySet().stream()
                .map(entry -> update(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static WriteModel<Document> update(String term, Set<Integer> ids) {
        return new UpdateOneModel<>(Filters.eq("term", term),
                Updates.addEachToSet("postings", new ArrayList<>(ids)),
                new UpdateOptions().upsert(true));
    }
}
