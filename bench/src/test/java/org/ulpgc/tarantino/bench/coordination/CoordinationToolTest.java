package org.ulpgc.tarantino.bench.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinationToolTest {

    private static final String PREFIX = " INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition ";

    @TempDir
    Path runDirectory;

    @Test
    void joinsTheTransitionsOfEveryServiceLogIntoOneRowPerPublishedBook() throws IOException {
        Path logs = Files.createDirectories(runDirectory.resolve("logs"));
        Files.write(logs.resolve("control.log"), List.of(
                "control-1  | 2026-10-21T10:00:00.002Z" + PREFIX + "entity=book id=84 from=- to=QUEUED batch_id=- generation=- enqueued_at=- at=2026-10-21T10:00:00Z replica=c",
                "control-1  | 2026-10-21T10:00:01Z INFO Seeded 1 books"));
        Files.write(logs.resolve("crawler.log"), List.of(
                "crawler-1  | 2026-10-21T10:00:00.5Z" + PREFIX + "entity=book id=84 from=QUEUED to=DOWNLOADING batch_id=- generation=- enqueued_at=2026-10-21T10:00:00.001Z at=2026-10-21T10:00:00.250Z replica=a",
                "crawler-1  | 2026-10-21T10:00:02Z" + PREFIX + "entity=book id=84 from=DOWNLOADING to=DOWNLOADED batch_id=- generation=- enqueued_at=- at=2026-10-21T10:00:01.750Z replica=a"));
        Files.write(logs.resolve("indexer.log"), List.of(
                "indexer-2  | 2026-10-21T10:00:02Z" + PREFIX + "entity=book id=84 from=DOWNLOADED to=INDEXING batch_id=b1 generation=1 enqueued_at=2026-10-21T10:00:01.751Z at=2026-10-21T10:00:01.900Z replica=b",
                "indexer-2  | 2026-10-21T10:00:04Z" + PREFIX + "entity=batch id=b1 from=WRITING to=PUBLISHED batch_id=- generation=1 enqueued_at=- at=2026-10-21T10:00:03.400Z replica=b"));
        Files.writeString(logs.resolve("notes.txt"), "not a service log");

        assertEquals(CoordinationTool.DONE, CoordinationTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(CoordinationTool.HEADER, "84,249,1500,149,1500,3400"),
                Files.readAllLines(runDirectory.resolve(CoordinationTool.FILE_NAME)));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(CoordinationTool.WRONG_USAGE, CoordinationTool.run(List.of()));
    }
}
