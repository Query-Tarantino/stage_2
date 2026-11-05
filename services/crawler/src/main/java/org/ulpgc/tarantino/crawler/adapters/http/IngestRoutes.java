package org.ulpgc.tarantino.crawler.adapters.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.common.http.BookIds;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.model.book.IngestList;
import org.ulpgc.tarantino.crawler.ports.BookStateReader;
import org.ulpgc.tarantino.crawler.ports.DownloadedBooks;
import org.ulpgc.tarantino.crawler.ports.IngestRequests;
import org.ulpgc.tarantino.crawler.ports.TaskQueue;

import java.time.Clock;

/** The routes of {@code crawler}, behind nginx's port 8081 (SPEC §6.1, §6.2 and §6.5). */
public class IngestRoutes {

    private static final String BOOK_STATUS = "/ingest/status/";

    private final BookStateReader states;
    private final IngestRequests requests;
    private final DownloadedBooks downloadedBooks;
    private final TaskQueue downloadQueue;
    private final TransitionLog log;
    private final Clock clock;

    public IngestRoutes(BookStateReader states, IngestRequests requests, DownloadedBooks downloadedBooks, TaskQueue downloadQueue,
                        TransitionLog log, Clock clock) {
        this.states = states;
        this.requests = requests;
        this.downloadedBooks = downloadedBooks;
        this.downloadQueue = downloadQueue;
        this.log = log;
        this.clock = clock;
    }

    public void register(Javalin app) {
        app.post("/ingest/{" + BookIds.PARAMETER + "}", this::ingest);
        app.get(BOOK_STATUS + "{" + BookIds.PARAMETER + "}", this::status);
        app.get("/ingest/list", context -> context.json(IngestList.of(downloadedBooks.downloaded())));
    }

    /**
     * “Create the state” or, if it existed, “Retry a {@code FAILED} book”, and the one that matches enqueues it; otherwise
     * the book is answered as it is, without enqueuing it again (SPEC §6.2).
     */
    private void ingest(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        if (queuedByThisRequest(bookId)) {
            downloadQueue.enqueue(new BookTask(bookId, clock.instant()));
            accepted(context, state(bookId));
            return;
        }
        BookState state = state(bookId);
        switch (state.status()) {
            case QUEUED, DOWNLOADING, FAILED -> accepted(context, state);
            case DOWNLOADED, INDEXING, PUBLISHED -> context.json(state);
        }
    }

    private boolean queuedByThisRequest(int bookId) {
        if (requests.create(bookId)) {
            log.write(Transition.book(bookId, null, BookStatus.QUEUED));
            return true;
        }
        if (requests.retry(bookId)) {
            log.write(Transition.book(bookId, BookStatus.FAILED, BookStatus.QUEUED));
            return true;
        }
        return false;
    }

    private void status(Context context) {
        int bookId = BookIds.parse(context.pathParam(BookIds.PARAMETER));
        context.json(state(bookId));
    }

    private static void accepted(Context context, BookState state) {
        context.status(HttpStatus.ACCEPTED).header(Header.LOCATION, BOOK_STATUS + state.bookId()).json(state);
    }

    private BookState state(int bookId) {
        return states.state(bookId)
                .orElseThrow(() -> new ProblemException(ProblemCode.BOOK_NOT_FOUND, "Book " + bookId + " has no state"));
    }
}
