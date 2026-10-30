package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.query.model.BookDetails;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot.BookFilter;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.AUTHOR_TERMS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LANGUAGES;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ORIGINAL_YEAR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.TITLE;
import static org.ulpgc.tarantino.common.mongo.MongoFields.YEAR;

public class MongoMetadataReader implements MetadataReader {

    private final MongoDatabase database;

    public MongoMetadataReader(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
        if (bookIds.isEmpty()) {
            return List.of();
        }
        return database.getCollection(MongoCollections.metadata(generation))
                .find(Filters.in(ID, bookIds))
                .projection(Projections.include(TITLE, AUTHOR, LANGUAGES, YEAR))
                .map(MongoMetadataReader::result)
                .into(new ArrayList<>());
    }

    @Override
    public Optional<BookDetails> bookDetails(int generation, int bookId) {
        Document book = database.getCollection(MongoCollections.metadata(generation))
                .find(Filters.eq(ID, bookId))
                .projection(Projections.include(TITLE, AUTHOR, LANGUAGES, YEAR, ORIGINAL_YEAR))
                .first();
        if (book == null) {
            return Optional.empty();
        }
        List<String> languages = book.getList(LANGUAGES, String.class, List.of());
        String language = languages.isEmpty() ? null : languages.getFirst();
        return Optional.of(new BookDetails(book.getInteger(ID), book.getString(TITLE), book.getString(AUTHOR),
                language, languages, book.getInteger(YEAR), book.getInteger(ORIGINAL_YEAR)));
    }

    @Override
    public Map<Integer, BookFilter> filters(int generation, Collection<Integer> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BookFilter> filters = new HashMap<>();
        database.getCollection(MongoCollections.metadata(generation))
                .find(Filters.in(ID, bookIds))
                .projection(Projections.include(ID, LANGUAGES, YEAR))
                .forEach(doc -> filters.put(doc.getInteger(ID),
                        new BookFilter(doc.getList(LANGUAGES, String.class, List.of()), doc.getInteger(YEAR))));
        return filters;
    }

    @Override
    public Set<Integer> matchingBookIds(int generation, Collection<String> authorTerms, String language, Integer year) {
        List<Bson> criteria = new ArrayList<>();
        if (authorTerms != null && !authorTerms.isEmpty()) {
            criteria.add(Filters.all(AUTHOR_TERMS, authorTerms));
        }
        if (language != null) {
            criteria.add(Filters.eq(LANGUAGES, language));
        }
        if (year != null) {
            criteria.add(Filters.eq(YEAR, year));
        }
        if (criteria.isEmpty()) {
            return Set.of();
        }
        Bson filter = criteria.size() == 1 ? criteria.getFirst() : Filters.and(criteria);
        return database.getCollection(MongoCollections.metadata(generation))
                .find(filter)
                .projection(Projections.include(ID))
                .map(doc -> doc.getInteger(ID))
                .into(new HashSet<>());
    }

    private static SearchResult result(Document book) {
        List<String> languages = book.getList(LANGUAGES, String.class, List.of());
        return new SearchResult(book.getInteger(ID), book.getString(TITLE), book.getString(AUTHOR),
                languages.isEmpty() ? null : languages.getFirst(), book.getInteger(YEAR));
    }
}
