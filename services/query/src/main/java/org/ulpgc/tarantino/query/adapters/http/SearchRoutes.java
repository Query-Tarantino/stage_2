package org.ulpgc.tarantino.query.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.Snapshot;

/** {@code GET /search} (SPEC §6.3): it validates the parameters first and then reads the snapshot once. */
public class SearchRoutes {

    private final SnapshotRefresher snapshots;
    private final SearchCommand search;

    public SearchRoutes(SnapshotRefresher snapshots, SearchCommand search) {
        this.snapshots = snapshots;
        this.search = search;
    }

    public void register(Javalin app) {
        app.get("/search", this::search);
    }

    private void search(Context context) {
        SearchQuery query = SearchParameters.query(context.queryParam(SearchParameters.Q),
                context.queryParam(SearchParameters.OFFSET), context.queryParam(SearchParameters.LIMIT));
        Snapshot snapshot = snapshots.current()
                .orElseThrow(() -> new ProblemException(ProblemCode.DEPENDENCY_UNAVAILABLE, "query has no snapshot yet"));
        context.json(search.execute(snapshot, query));
    }
}
