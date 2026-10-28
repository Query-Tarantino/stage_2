package org.ulpgc.tarantino.indexer.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.ulpgc.tarantino.common.http.BookIds;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.indexer.ports.BookStateReader;

/** The routes of {@code indexer} about one book, behind nginx's port 8081 (SPEC §6.2). */
public class BookRoutes {

    private final BookStateReader states;

    public BookRoutes(BookStateReader states) {
        this.states = states;
    }

    public void register(Javalin app) {
        app.get("/index/status/{" + BookIds.PARAMETER + "}", this::status);
    }

    private void status(Context context) {
        context.json(state(context));
    }

    private BookState state(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        return states.state(bookId)
                .orElseThrow(() -> new ProblemException(ProblemCode.BOOK_NOT_FOUND, "Book " + bookId + " has no state"));
    }
}
