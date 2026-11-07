package org.ulpgc.tarantino.query.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.common.http.BookIds;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;
import org.ulpgc.tarantino.query.model.BookDetails;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.List;
import java.util.Set;

/** Javalin HTTP routes for /search, /books/{book_id} and /search/filters (SPEC §6.3, §6.4). */
public class SearchRoutes {

    private static final String SEARCH_PATH = "/search";
    private static final String BOOK_PATH = "/books/{" + BookIds.PARAMETER + "}";
    private static final String FILTERS_PATH = "/search/filters";
    private static final String ALLOWED_METHODS = "GET, OPTIONS";
    private static final String ANY_HEADER = "*";
    private static final String ANY_ORIGIN = "*";

    private final SnapshotRefresher snapshots;
    private final SearchCommand search;
    private final MetadataReader metadata;
    private final Tokenizer tokenizer;
    private final String corsOrigin;

    public SearchRoutes(SnapshotRefresher snapshots, SearchCommand search) {
        this(snapshots, search, (generation, bookIds) -> List.of(), new Tokenizer(Set.of()), ANY_ORIGIN);
    }

    public SearchRoutes(
            SnapshotRefresher snapshots,
            SearchCommand search,
            MetadataReader metadata,
            Tokenizer tokenizer,
            String corsOrigin) {
        this.snapshots = snapshots;
        this.search = search;
        this.metadata = metadata;
        this.tokenizer = tokenizer;
        this.corsOrigin = corsOrigin;
    }

    public void register(Javalin app) {
        if (corsOrigin != null && !corsOrigin.isBlank()) {
            app.before(context -> context.header(Header.ACCESS_CONTROL_ALLOW_ORIGIN, corsOrigin));
        }
        app.get(SEARCH_PATH, this::search);
        app.get(BOOK_PATH, this::book);
        app.get(FILTERS_PATH, this::filters);
        app.options(SEARCH_PATH, this::options);
        app.options(BOOK_PATH, this::options);
        app.options(FILTERS_PATH, this::options);
    }

    private void options(Context context) {
        if (corsOrigin != null && !corsOrigin.isBlank()) {
            context.header(Header.ACCESS_CONTROL_ALLOW_ORIGIN, corsOrigin);
            context.header(Header.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS);
            context.header(Header.ACCESS_CONTROL_ALLOW_HEADERS, ANY_HEADER);
        }
        context.status(HttpStatus.OK);
    }

    private void search(Context context) {
        SearchQuery query = SearchParameters.query(
                tokenizer,
                context.queryParam("q"),
                context.queryParam("author"),
                context.queryParam("language"),
                context.queryParam("year"),
                context.queryParam("offset"),
                context.queryParam("limit"));
        Snapshot snapshot = snapshot();
        context.json(search.execute(snapshot, query));
    }

    private void book(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        Snapshot snapshot = snapshot();
        if (!snapshot.bookIds().contains(bookId)) {
            throw new ProblemException(
                    ProblemCode.BOOK_NOT_FOUND, "book " + bookId + " is not in the snapshot");
        }
        BookDetails details = metadata.bookDetails(snapshot.generation(), bookId)
                .orElseThrow(() -> new ProblemException(
                        ProblemCode.BOOK_NOT_FOUND, "book " + bookId + " was not found"));
        context.json(details);
    }

    private void filters(Context context) {
        context.json(snapshot().filtersSummary());
    }

    private Snapshot snapshot() {
        return snapshots.current().orElseThrow(() -> new ProblemException(
                ProblemCode.DEPENDENCY_UNAVAILABLE, "query has no snapshot yet"));
    }
}