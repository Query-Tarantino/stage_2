package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.ports.BookStateStore;
import org.ulpgc.tarantino.control.ports.DownloadQueue;

import java.time.Clock;
import java.util.List;

/** Seeding (SPEC §5.3): “Create the state” for each candidate, and a {@link BookTask} only for the ones it created. */
public class SeedCommand {

    private final BookStateStore states;
    private final DownloadQueue queue;
    private final TransitionLog log;
    private final Clock clock;

    public SeedCommand(BookStateStore states, DownloadQueue queue, TransitionLog log, Clock clock) {
        this.states = states;
        this.queue = queue;
        this.log = log;
        this.clock = clock;
    }

    /** How many candidates it created; the others already had a state and are left as they are. */
    public int execute(List<Integer> candidates) {
        int created = 0;
        for (int bookId : candidates) {
            if (seed(bookId)) {
                created++;
            }
        }
        return created;
    }

    private boolean seed(int bookId) {
        if (!states.create(bookId)) {
            return false;
        }
        log.write(Transition.book(bookId, null, BookStatus.QUEUED));
        queue.enqueue(new BookTask(bookId, clock.instant()));
        return true;
    }
}
