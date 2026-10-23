package org.ulpgc.tarantino.common.json;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TarantinoJsonTest {

    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));
    private static final BatchPublished BATCH =
            new BatchPublished("6716f0c2a1b2c3d4e5f60718", 1, 100, Instant.parse("2026-10-21T10:06:00Z"));

    @Test
    void writesBookTasksInSnakeCaseWithTextDates() {
        assertEquals("{\"book_id\":1342,\"enqueued_at\":\"2026-10-21T10:05:00Z\"}", TarantinoJson.write(TASK));
    }

    @Test
    void writesPublishedBatchesInSnakeCaseWithTextDates() {
        assertEquals("{\"batch_id\":\"6716f0c2a1b2c3d4e5f60718\",\"generation\":1,\"book_count\":100,"
                + "\"published_at\":\"2026-10-21T10:06:00Z\"}", TarantinoJson.write(BATCH));
    }

    @Test
    void readsWhatItWrites() {
        assertEquals(TASK, TarantinoJson.read(TarantinoJson.write(TASK), BookTask.class));
        assertEquals(BATCH, TarantinoJson.read(TarantinoJson.write(BATCH), BatchPublished.class));
    }

    @Test
    void readsDatesWithAFractionOfASecond() {
        assertEquals(Instant.parse("2026-10-21T10:05:02.481Z"),
                TarantinoJson.read("{\"book_id\":1342,\"enqueued_at\":\"2026-10-21T10:05:02.481Z\"}", BookTask.class).enqueuedAt());
    }

    @Test
    void ignoresUnknownFields() {
        assertEquals(TASK, TarantinoJson.read(
                "{\"book_id\":1342,\"enqueued_at\":\"2026-10-21T10:05:00Z\",\"priority\":\"high\"}", BookTask.class));
    }
}
