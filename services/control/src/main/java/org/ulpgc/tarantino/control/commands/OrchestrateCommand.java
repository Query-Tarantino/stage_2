package org.ulpgc.tarantino.control.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.model.DispatchedBooks;
import org.ulpgc.tarantino.control.model.Stage;
import org.ulpgc.tarantino.control.ports.WaitingBooks;
import org.ulpgc.tarantino.control.ports.WorkDispatcher;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One tick of the orchestrator of mode {@code orchestrator} (SPEC §11.4): it drops the books that left their waiting
 * state, dispatches the oldest waiting books of each stage, one {@code POST} at a time, up to its window and until the
 * first refusal, dispatches again the books whose last dispatch is older than {@code TARANTINO_REQUEUE_SECONDS}, and
 * then announces the new published batches.
 */
public class OrchestrateCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestrateCommand.class);

    private final WaitingBooks waiting;
    private final WorkDispatcher dispatcher;
    private final Map<Stage, DispatchedBooks> dispatched;
    private final Map<Stage, Integer> windows;
    private final Duration requeue;
    private final Runnable announce;
    private final TransitionLog log;
    private final Clock clock;

    public OrchestrateCommand(WaitingBooks waiting, WorkDispatcher dispatcher, Map<Stage, DispatchedBooks> dispatched,
                              Map<Stage, Integer> windows, Duration requeue, Runnable announce, TransitionLog log,
                              Clock clock) {
        this.waiting = waiting;
        this.dispatcher = dispatcher;
        this.dispatched = new EnumMap<>(dispatched);
        this.windows = new EnumMap<>(windows);
        this.requeue = requeue;
        this.announce = announce;
        this.log = log;
        this.clock = clock;
    }

    /** One tick; if Mongo fails it logs it and returns, so the scheduled loop keeps running. */
    public void tick() {
        try {
            settle();
            List.of(Stage.values()).forEach(this::dispatchWaiting);
            List.of(Stage.values()).forEach(this::dispatchAgain);
            announce.run();
        } catch (RuntimeException e) {
            LOGGER.warn("Orchestrator tick stopped, the next one starts over: {}", e.getMessage());
        }
    }

    private void settle() {
        Set<Integer> inFlight = dispatched.values().stream().flatMap(books -> books.bookIds().stream()).collect(Collectors.toSet());
        Map<Integer, BookStatus> statuses = inFlight.isEmpty() ? Map.of() : waiting.statuses(inFlight);
        dispatched.forEach((stage, books) -> books.keepOnly(bookId -> statuses.get(bookId) == stage.waitingStatus()));
    }

    private void dispatchWaiting(Stage stage) {
        DispatchedBooks books = dispatched.get(stage);
        int room = books.room(windows.get(stage));
        if (room == 0) {
            return;
        }
        for (BookTask task : waiting.oldest(stage.waitingStatus(), books.bookIds(), room)) {
            if (!dispatcher.dispatch(stage, task)) {
                return;
            }
            books.dispatched(task.bookId(), task.enqueuedAt(), clock.instant());
        }
    }

    private void dispatchAgain(Stage stage) {
        DispatchedBooks books = dispatched.get(stage);
        for (DispatchedBooks.Dispatch dispatch : books.dueAgain(clock.instant(), requeue)) {
            if (!dispatcher.dispatch(stage, new BookTask(dispatch.bookId(), dispatch.enqueuedAt()))) {
                return;
            }
            books.dispatched(dispatch.bookId(), dispatch.enqueuedAt(), clock.instant());
            log.write(Transition.book(dispatch.bookId(), stage.waitingStatus(), stage.waitingStatus()));
        }
    }
}
