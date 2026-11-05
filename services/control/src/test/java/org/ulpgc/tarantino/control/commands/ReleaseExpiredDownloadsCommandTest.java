package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.model.DownloadLease;
import org.ulpgc.tarantino.control.ports.DownloadLeases;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseExpiredDownloadsCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:00Z");

    private final ExpiredLeases leases = new ExpiredLeases();
    private final List<BookTask> queue = new ArrayList<>();
    private final RecordingTransitionLog log = new RecordingTransitionLog();
    private final ReleaseExpiredDownloadsCommand release =
            new ReleaseExpiredDownloadsCommand(leases, queue::add, log, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void enqueuesOnlyTheDownloadsLeftQueued() {
        releaseOneQueuedOneFailedAndOneRenewed();

        assertEquals(List.of(new BookTask(1342, NOW)), queue);
    }

    @Test
    void logsOnlyTheReleasesThatMatched() {
        releaseOneQueuedOneFailedAndOneRenewed();

        assertEquals(List.of(Transition.book(1342, BookStatus.DOWNLOADING, BookStatus.QUEUED),
                Transition.book(84, BookStatus.DOWNLOADING, BookStatus.FAILED)), log.transitions());
    }

    private void releaseOneQueuedOneFailedAndOneRenewed() {
        leases.expire(new DownloadLease(1342, "first-claim"), Optional.of(BookStatus.QUEUED));
        leases.expire(new DownloadLease(84, "second-claim"), Optional.of(BookStatus.FAILED));
        leases.expire(new DownloadLease(11, "renewed-claim"), Optional.empty());
        release.execute();
    }

    private static final class ExpiredLeases implements DownloadLeases {

        private final Map<DownloadLease, Optional<BookStatus>> outcomes = new LinkedHashMap<>();

        void expire(DownloadLease lease, Optional<BookStatus> outcome) {
            outcomes.put(lease, outcome);
        }

        @Override
        public List<DownloadLease> expired() {
            return List.copyOf(outcomes.keySet());
        }

        @Override
        public Optional<BookStatus> release(DownloadLease lease) {
            return outcomes.get(lease);
        }
    }
}
