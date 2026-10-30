package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.model.DownloadLease;
import org.ulpgc.tarantino.control.ports.DownloadLeases;
import org.ulpgc.tarantino.control.ports.DownloadQueue;

import java.time.Clock;

/** Step 1 of the reconciliation (SPEC §5.3): releases every expired download and enqueues the ones left {@code QUEUED}. */
public class ReleaseExpiredDownloadsCommand {

    private final DownloadLeases leases;
    private final DownloadQueue queue;
    private final TransitionLog log;
    private final Clock clock;

    public ReleaseExpiredDownloadsCommand(DownloadLeases leases, DownloadQueue queue, TransitionLog log, Clock clock) {
        this.leases = leases;
        this.queue = queue;
        this.log = log;
        this.clock = clock;
    }

    public void execute() {
        leases.expired().forEach(this::release);
    }

    private void release(DownloadLease lease) {
        leases.release(lease).ifPresent(status -> released(lease.bookId(), status));
    }

    private void released(int bookId, BookStatus status) {
        log.write(Transition.book(bookId, BookStatus.DOWNLOADING, status));
        if (status == BookStatus.QUEUED) {
            queue.enqueue(new BookTask(bookId, clock.instant()));
        }
    }
}
