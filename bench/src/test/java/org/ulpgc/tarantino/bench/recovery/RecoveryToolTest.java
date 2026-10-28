package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryToolTest {

    @Test
    void waitsOnlyForABookStateOrAQueue() {
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.waitFor(List.of()));
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.waitFor(List.of("downloading")));
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.waitFor(List.of("DOWNLOADING", "INDEXING")));
    }

    @Test
    void recoversOnlyATwoDigitScriptWithAnIsoFailureInstant() {
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.recovery(List.of("4", "run", "2026-11-10T15:30:00Z")));
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.recovery(List.of("04", "run", "2026-11-10 15:30:00")));
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.recovery(List.of("04", "run")));
    }

    @Test
    void comparesOnlyATwoDigitScript() {
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.compare(List.of("4")));
        assertEquals(RecoveryTool.WRONG_USAGE, RecoveryTool.compare(List.of()));
    }
}
