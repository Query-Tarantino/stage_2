package org.ulpgc.tarantino.indexer.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.indexer.ports.Rebuilds;

/** The operational routes of {@code indexer}, behind nginx's port 8081 (SPEC §6.1 and §6.5). */
public class IndexRoutes {

    private static final String INDEX_STATUS = "/index/status";

    private final Rebuilds rebuilds;

    public IndexRoutes(Rebuilds rebuilds) {
        this.rebuilds = rebuilds;
    }

    public void register(Javalin app) {
        app.post("/index/rebuild", this::rebuild);
    }

    /** {@code 202} with the rebuild just opened, or the one already running, and where to follow it. */
    private void rebuild(Context context) {
        context.status(HttpStatus.ACCEPTED).header(Header.LOCATION, INDEX_STATUS).json(rebuilds.open());
    }
}
