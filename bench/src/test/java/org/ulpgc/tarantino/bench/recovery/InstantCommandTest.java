package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstantCommandTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00.123456789Z"), ZoneOffset.UTC);

    @Test
    void givesNowToTheMillisecond() {
        assertEquals(Optional.of(Instant.parse("2026-10-29T12:00:00.123Z")), InstantCommand.instant(List.of(), CLOCK));
    }

    @Test
    void addsWholeSecondsForAPlannedFailure() {
        assertEquals(Optional.of(Instant.parse("2026-10-29T12:00:20.123Z")), InstantCommand.instant(List.of("20"), CLOCK));
    }

    @Test
    void rejectsAnythingButWholeSeconds() {
        assertEquals(Optional.empty(), InstantCommand.instant(List.of("-5"), CLOCK));
        assertEquals(Optional.empty(), InstantCommand.instant(List.of("2.5"), CLOCK));
        assertEquals(Optional.empty(), InstantCommand.instant(List.of("20", "30"), CLOCK));
    }
}
