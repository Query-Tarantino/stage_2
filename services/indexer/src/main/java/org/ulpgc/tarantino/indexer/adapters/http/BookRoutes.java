package org.ulpgc.tarantino.indexer.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.common.http.BookIds;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.indexer.ports.BookStateReader;
import org.ulpgc.tarantino.indexer.ports.BooksToIndex;

import java.time.Clock;

/** The routes of {@code indexer} about one book, behind nginx's port 8081 (SPEC §6.2). */
public class BookRoutes {

    private static final String BOOK_STATUS = "/index/status/";

    private final BookStateReader states;
    private final BooksToIndex booksToIndex;
    private final Clock clock;

    public BookRoutes(BookStateReader states, BooksToIndex booksToIndex, Clock clock) {
        this.states = states;
        this.booksToIndex = booksToIndex;
        this.clock = clock;
    }

    public void register(Javalin app) {
        app.post("/index/update/{" + BookIds.PARAMETER + "}", this::update);
        app.get(BOOK_STATUS + "{" + BookIds.PARAMETER + "}", this::status);
    }

    /** Only a {@code DOWNLOADED} book is enqueued; one already in a batch is accepted as it is (SPEC §6.2). */
    private void update(Context context) {
        BookState state = state(context);
        switch (state.status()) {
            case QUEUED, DOWNLOADING, FAILED -> throw new ProblemException(ProblemCode.NOT_DOWNLOADED, "Book " + state.bookId()
                    + " is " + state.status() + ": only a DOWNLOADED book is indexed, and a FAILED one goes back through POST /ingest");
            case DOWNLOADED -> {
                booksToIndex.enqueue(new BookTask(state.bookId(), clock.instant()));
                accepted(context, state);
            }
            case INDEXING -> accepted(context, state);
            case PUBLISHED -> context.json(state);
        }
    }

    private void status(Context context) {
        context.json(state(context));
    }

    private static void accepted(Context context, BookState state) {
        context.status(HttpStatus.ACCEPTED).header(Header.LOCATION, BOOK_STATUS + state.bookId()).json(state);
    }

    private BookState state(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        return states.state(bookId)
                .orElseThrow(() -> new ProblemException(ProblemCode.BOOK_NOT_FOUND, "Book " + bookId + " has no state"));
    }
}
