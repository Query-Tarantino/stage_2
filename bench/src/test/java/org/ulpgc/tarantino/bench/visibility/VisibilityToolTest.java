package org.ulpgc.tarantino.bench.visibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityToolTest {

    private static final String BATCH = "6716f0c2a1b2c3d4e5f60718";
    private static final String PUBLISHED = "indexer-1  | 2026-10-21T10:06:00.000Z [index-batches] INFO TransitionLog - transition entity=batch id="
            + BATCH + " from=WRITING to=PUBLISHED batch_id=- generation=1 enqueued_at=- at=2026-10-21T10:06:00.000Z replica=ix1";

    @TempDir
    Path run;

    @Test
    void joinsEachReplicaFirstSnapshotLineWithThePublication() throws IOException {
        trial("listener-on-rep-1", List.of(
                snapshot("q1", "2026-10-21T10:06:00.040Z"),
                snapshot("q2", "2026-10-21T10:06:00.090Z"),
                snapshot("q1", "2026-10-21T10:06:09.000Z")));
        trial("listener-on-rep-2", List.of(snapshot("q1", "2026-10-21T10:06:00.060Z")));

        assertEquals(0, VisibilityTool.run(List.of(run.toString())));

        assertEquals(List.of(VisibilityTool.HEADER,
                "listener-on-rep-1," + BATCH + ",q1,2026-10-21T10:06:00Z,2026-10-21T10:06:00.040Z,40",
                "listener-on-rep-1," + BATCH + ",q2,2026-10-21T10:06:00Z,2026-10-21T10:06:00.090Z,90",
                "listener-on-rep-2," + BATCH + ",q1,2026-10-21T10:06:00Z,2026-10-21T10:06:00.060Z,60"),
                Files.readAllLines(run.resolve(VisibilityTool.FILE_NAME)));
        String summary = Files.readString(run.resolve("summary.csv"));
        assertTrue(summary.contains("publication_visibility,\"listener=on\",62.50,3.54,31.77,60.00,90.00,90.00,ms"), summary);
    }

    private void trial(String name, List<String> queryLines) throws IOException {
        Path logs = Files.createDirectories(run.resolve("logs").resolve(name));
        Files.writeString(logs.resolve("indexer.log"), PUBLISHED + "\n");
        Files.write(logs.resolve("query.log"), queryLines);
    }

    private static String snapshot(String replica, String at) {
        return "query-1  | " + at + " [snapshot] INFO SnapshotLog - snapshot batch_id=" + BATCH + " generation=1 at=" + at
                + " replica=" + replica;
    }
}
