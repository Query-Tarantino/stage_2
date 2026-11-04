package org.ulpgc.tarantino.common.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * {@code POST /internal/download} and {@code POST /internal/index} of mode {@code orchestrator} (SPEC §11.4): the
 * handler only validates the {@code BookTask} and offers it to the replica's local queue, {@code 202} if it went in and
 * {@code 503} with {@code Retry-After: 1} if the queue is full, so nginx hands it to the next replica. It never waits
 * for the work.
 */
public final class InternalTasks {

    public static final String DOWNLOAD = "/internal/download";
    public static final String INDEX = "/internal/index";

    private static final Duration RETRY_AFTER_FULL = Duration.ofSeconds(1);
    private static final String BODY = "body";
    private static final String FULL = "The local queue of this replica is full";

    private InternalTasks() {
    }

    public static void register(Javalin app, String path, Predicate<BookTask> localQueue) {
        app.post(path, context -> accept(context, localQueue));
    }

    private static void accept(Context context, Predicate<BookTask> localQueue) {
        BookTask task = task(context);
        if (!localQueue.test(task)) {
            ProblemDetails.unavailable(context, FULL, RETRY_AFTER_FULL);
            return;
        }
        context.status(HttpStatus.ACCEPTED);
    }

    private static BookTask task(Context context) {
        BookTask task = parsed(context.body());
        if (task == null || task.bookId() < 1) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, BookIds.PARAMETER, "book_id must be a positive integer");
        }
        if (task.enqueuedAt() == null) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, "enqueued_at", "enqueued_at must be an ISO-8601 instant");
        }
        return task;
    }

    private static BookTask parsed(String body) {
        try {
            return TarantinoJson.read(body, BookTask.class);
        } catch (RuntimeException e) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, BODY, "The body must be a BookTask: " + e.getMessage());
        }
    }
}
