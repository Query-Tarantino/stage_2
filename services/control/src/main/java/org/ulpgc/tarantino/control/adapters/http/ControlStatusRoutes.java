package org.ulpgc.tarantino.control.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.control.commands.ControlStatusCommand;
import org.ulpgc.tarantino.control.model.ControlStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code GET /control/status} (SPEC §6.5), with the queues by their names and {@code null} before any reconciliation. */
public class ControlStatusRoutes {

    private final ControlStatusCommand status;

    public ControlStatusRoutes(ControlStatusCommand status) {
        this.status = status;
    }

    public void register(Javalin app) {
        app.get("/control/status", this::status);
    }

    private void status(Context context) {
        context.json(StatusBody.of(status.execute()));
    }

    record StatusBody(int generation, Map<BookStatus, Long> books, Map<String, Integer> queues, Instant lastReconciliation) {

        static StatusBody of(ControlStatus status) {
            Map<String, Integer> queues = new LinkedHashMap<>();
            queues.put(HazelcastContracts.BOOKS_TO_DOWNLOAD, status.downloadQueue());
            queues.put(HazelcastContracts.BOOKS_TO_INDEX, status.indexQueue());
            return new StatusBody(status.generation(), status.books(), queues, status.lastReconciliation().orElse(null));
        }
    }
}
