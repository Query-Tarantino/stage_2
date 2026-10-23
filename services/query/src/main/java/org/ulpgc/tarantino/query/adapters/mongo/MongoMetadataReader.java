package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LANGUAGES;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TITLE;
import static org.ulpgc.tarantino.common.mongo.MongoFields.YEAR;

public class MongoMetadataReader implements MetadataReader {

    private final MongoDatabase database;

    public MongoMetadataReader(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
        return database.getCollection(MongoCollections.metadata(generation))
                .find(Filters.in(ID, bookIds))
                .projection(Projections.include(TITLE, AUTHOR, LANGUAGES, YEAR))
                .map(MongoMetadataReader::result)
                .into(new ArrayList<>());
    }

    private static SearchResult result(Document book) {
        List<String> languages = book.getList(LANGUAGES, String.class, List.of());
        return new SearchResult(book.getInteger(ID), book.getString(TITLE), book.getString(AUTHOR),
                languages.isEmpty() ? null : languages.getFirst(), book.getInteger(YEAR));
    }
}
