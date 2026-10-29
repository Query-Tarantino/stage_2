package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.PostingsReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TERM;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TFS;

public class MongoPostingsReader implements PostingsReader {

    private final MongoDatabase database;

    public MongoPostingsReader(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public List<TermPostings> postings(int generation, Collection<String> terms, Collection<String> batchIds) {
        if (terms.isEmpty() || batchIds.isEmpty()) {
            return List.of();
        }
        return database.getCollection(MongoCollections.postings(generation))
                .find(Filters.and(Filters.in(TERM, terms), Filters.in(BATCH_ID, batchIds.stream().map(ObjectId::new).toList())))
                .projection(Projections.include(TERM, BOOK_IDS, TFS))
                .map(posting -> new TermPostings(posting.getString(TERM), posting.getList(BOOK_IDS, Integer.class),
                        posting.getList(TFS, Integer.class)))
                .into(new ArrayList<>());
    }
}
