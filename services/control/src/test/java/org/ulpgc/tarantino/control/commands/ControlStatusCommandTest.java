package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.control.model.ControlStatus;
import org.ulpgc.tarantino.control.ports.QueueSizes;
import org.ulpgc.tarantino.control.ports.StatusCounts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlStatusCommandTest {

    private static final Instant LAST_RECONCILIATION = Instant.parse("2026-10-21T10:06:30Z");

    @Test
    void reportsEveryStateEvenWithoutBooksInIt() {
        ControlStatusCommand status = new ControlStatusCommand(new Counts(1, Map.of(BookStatus.QUEUED, 2L, BookStatus.PUBLISHED, 3L)),
                new Sizes(1, 0), () -> Optional.of(LAST_RECONCILIATION));

        ControlStatus reported = status.execute();

        assertEquals(new ControlStatus(1, Map.of(BookStatus.QUEUED, 2L, BookStatus.DOWNLOADING, 0L, BookStatus.DOWNLOADED, 0L,
                BookStatus.INDEXING, 0L, BookStatus.PUBLISHED, 3L, BookStatus.FAILED, 0L), 1, 0, Optional.of(LAST_RECONCILIATION)), reported);
        assertEquals(List.of(BookStatus.values()), List.copyOf(reported.books().keySet()));
    }

    private record Counts(int currentGeneration, Map<BookStatus, Long> booksByStatus) implements StatusCounts {
    }

    private record Sizes(int downloads, int index) implements QueueSizes {
    }
}
