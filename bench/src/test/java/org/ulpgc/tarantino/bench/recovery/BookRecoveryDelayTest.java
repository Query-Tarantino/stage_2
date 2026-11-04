package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookRecoveryDelayTest {

    private static final Instant FAILED_AT = Instant.parse("2026-11-03T09:19:18Z");

    @Test
    void measuresTheRequeueAndTheNextClaimAfterIt() {
        List<TransitionLine> lines = List.of(
                book(1342, "QUEUED", "DOWNLOADING", "2026-11-03T09:19:10Z"),
                book(1342, "QUEUED", "QUEUED", "2026-11-03T09:19:28Z"),
                book(1342, "QUEUED", "DOWNLOADING", "2026-11-03T09:19:29Z"));

        assertEquals(List.of(new BookRecoveryDelay(1342, "QUEUED", Optional.of(Duration.ofSeconds(10)), Duration.ofSeconds(11))),
                BookRecoveryDelay.of(lines, FAILED_AT));
    }

    @Test
    void countsTheReclaimFromTheFailureWhenTheBrokerRedeliveredWithoutARequeue() {
        List<TransitionLine> lines = List.of(book(84, "DOWNLOADED", "INDEXING", "2026-11-03T09:19:21Z"));

        assertEquals(List.of(new BookRecoveryDelay(84, "DOWNLOADED", Optional.empty(), Duration.ofSeconds(3))),
                BookRecoveryDelay.of(lines, FAILED_AT));
    }

    @Test
    void readsTheModeAndTheReplicasFromTheRunName() {
        assertEquals(Optional.of(new RecoveryRunName("04", "hazelcast", 1)),
                RecoveryRunName.parse("20261103-091919-recovery-04-r1-05e4b5a"));
        assertEquals(Optional.of(new RecoveryRunName("12", "activemq-persistent", 3)),
                RecoveryRunName.parse("20261110-153000-recovery-12-activemq-persistent-r3-3f2a9c1"));
        assertEquals(Optional.empty(), RecoveryRunName.parse("20261110-153000-search-latency-3f2a9c1"));
    }

    private static TransitionLine book(int bookId, String from, String to, String at) {
        return new TransitionLine("book", String.valueOf(bookId), from, to, null, null, null, Instant.parse(at));
    }
}
