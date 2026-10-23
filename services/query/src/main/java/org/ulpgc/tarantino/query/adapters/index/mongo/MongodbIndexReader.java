package org.ulpgc.tarantino.query.adapters.index.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.ulpgc.tarantino.query.adapters.mongo.MongoDatabases;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;

import java.util.Optional;
import java.util.Set;

public class MongodbIndexReader implements InvertedIndexReader {

    private static final String COLLECTION = "inverted_index";

    private final MongoCollection<Document> collection;

    public MongodbIndexReader(String connectionUri) {
        this.collection = MongoDatabases.database(connectionUri).getCollection(COLLECTION);
    }

    @Override
    public Set<Integer> postings(String term) {
        return Optional.ofNullable(collection.find(Filters.eq("term", term)).first())
                .map(document -> Set.copyOf(document.getList("postings", Integer.class)))
                .orElseGet(Set::of);
    }
}
