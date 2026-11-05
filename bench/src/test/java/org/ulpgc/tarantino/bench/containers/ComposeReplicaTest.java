package org.ulpgc.tarantino.bench.containers;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComposeReplicaTest {

    @Test
    void readsTheServiceAndReplicaOfAComposeContainer() {
        assertEquals(Optional.of(new ComposeReplica("crawler", 3)), ComposeReplica.of("stage_2-crawler-3"));
        assertEquals(Optional.of(new ComposeReplica("mongo", 1)), ComposeReplica.of("wt-bench-mongo-1"));
    }

    @Test
    void readsNothingFromAContainerNotNamedAfterAService() {
        assertEquals(Optional.empty(), ComposeReplica.of("stage_2-bench-run-e1218bd40d63"));
        assertEquals(Optional.empty(), ComposeReplica.of("--"));
    }
}
