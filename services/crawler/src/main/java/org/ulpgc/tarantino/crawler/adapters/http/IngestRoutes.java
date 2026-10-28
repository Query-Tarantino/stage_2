package org.ulpgc.tarantino.crawler.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.ulpgc.tarantino.common.http.BookIds;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.crawler.ports.BookStateReader;

/** The routes of {@code crawler}, behind nginx's port 8081 (SPEC §6.1 and §6.2). */
public class IngestRoutes {

    private static final String BOOK_STATUS = "/ingest/status/";

    private final BookStateReader states;

    public IngestRoutes(BookStateReader states) {
        this.states = states;
    }

    public void register(Javalin app) {
        app.get(BOOK_STATUS + "{" + BookIds.PARAMETER + "}", this::status);
    }

    private void status(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        context.json(state(bookId));
    }

    private BookState state(int bookId) {
        return states.state(bookId)
                .orElseThrow(() -> new ProblemException(ProblemCode.BOOK_NOT_FOUND, "Book " + bookId + " has no state"));
    }
}
