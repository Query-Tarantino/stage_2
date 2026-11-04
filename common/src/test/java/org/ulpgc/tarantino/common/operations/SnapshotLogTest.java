package org.ulpgc.tarantino.common.operations;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotLogTest {

    @Test
    void writesTheKeysOfTheSpecInTheirOrder() {
        SnapshotLog log = new SnapshotLog("3f2b8c1e9a4d", Clock.fixed(Instant.parse("2026-10-21T10:06:00.250Z"), ZoneOffset.UTC));

        assertEquals("snapshot batch_id=6716f0c2a1b2c3d4e5f60718 generation=1 at=2026-10-21T10:06:00.250Z replica=3f2b8c1e9a4d",
                log.line("6716f0c2a1b2c3d4e5f60718", 1));
    }
}
