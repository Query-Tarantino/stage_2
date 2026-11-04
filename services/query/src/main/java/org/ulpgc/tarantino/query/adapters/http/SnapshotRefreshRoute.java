package org.ulpgc.tarantino.query.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

/**
 * {@code POST /internal/snapshot/refresh} of mode {@code orchestrator} (SPEC §11.4): it only asks the snapshot thread for a
 * refresh, as the {@code published-batches} listener does, and answers {@code 202} at once.
 */
public class SnapshotRefreshRoute {

    public static final String PATH = "/internal/snapshot/refresh";

    private final Runnable askForRefresh;

    public SnapshotRefreshRoute(Runnable askForRefresh) {
        this.askForRefresh = askForRefresh;
    }

    public void register(Javalin app) {
        app.post(PATH, context -> {
            askForRefresh.run();
            context.status(HttpStatus.ACCEPTED);
        });
    }
}
