package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.ports.DownloadQueue;
import org.ulpgc.tarantino.control.ports.IndexQueue;
import org.ulpgc.tarantino.control.ports.LostMessages;
import org.ulpgc.tarantino.control.ports.QueueSizes;

import java.time.Clock;
import java.util.function.Consumer;

/**
 * Step 4 of the reconciliation (SPEC §5.3): when a queue is empty, requeues the books that have waited for its message
 * too long, {@code QUEUED} ones into {@code books-to-download} and {@code DOWNLOADED} ones into {@code books-to-index}.
 */
public class RequeueLostMessagesCommand {

    private final QueueSizes sizes;
    private final LostMessages messages;
    private final DownloadQueue downloadQueue;
    private final IndexQueue indexQueue;
    private final TransitionLog log;
    private final Clock clock;

    public RequeueLostMessagesCommand(QueueSizes sizes, LostMessages messages, DownloadQueue downloadQueue,
                                      IndexQueue indexQueue, TransitionLog log, Clock clock) {
        this.sizes = sizes;
        this.messages = messages;
        this.downloadQueue = downloadQueue;
        this.indexQueue = indexQueue;
        this.log = log;
        this.clock = clock;
    }

    public void execute() {
        requeueIfEmpty(sizes.downloads(), BookStatus.QUEUED, downloadQueue::enqueue);
        requeueIfEmpty(sizes.index(), BookStatus.DOWNLOADED, indexQueue::enqueue);
    }

    private void requeueIfEmpty(int waiting, BookStatus status, Consumer<BookTask> queue) {
        if (waiting > 0) {
            return;
        }
        messages.lost(status).stream()
                .filter(bookId -> messages.requeue(bookId, status))
                .forEach(bookId -> requeued(bookId, status, queue));
    }

    private void requeued(int bookId, BookStatus status, Consumer<BookTask> queue) {
        log.write(Transition.book(bookId, status, status));
        queue.accept(new BookTask(bookId, clock.instant()));
    }
}
