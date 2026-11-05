package org.ulpgc.tarantino.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarantinoConfigTest {

    @Test
    void takesTheDefaultsOfTheSpec() {
        TarantinoConfig config = TarantinoConfig.from(Map.of(), TarantinoService.CRAWLER);

        assertEquals(new TarantinoConfig(7001, OptionalInt.empty(), "mongodb://localhost:27017", List.of("localhost:5701"),
                "tarantino", Path.of("datalake"), Path.of("workload"), Optional.empty(), "sample_ids.txt", 8, 3, 100,
                Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ofSeconds(30),
                Duration.ofSeconds(120), Duration.ofSeconds(600), Duration.ofSeconds(600), Duration.ofSeconds(3600),
                Duration.ofSeconds(5), "*", Optional.empty(), Coordination.HAZELCAST, true,
                "failover:(tcp://localhost:61616)?timeout=1000", false, Duration.ofMillis(200), Duration.ofMillis(200), 512, 400,
                "http://nginx:8082", 256, 200), config);
    }

    @Test
    void givesEachServiceItsOwnDefaultPort() {
        assertEquals(7002, TarantinoConfig.from(Map.of(), TarantinoService.INDEXER).port());
        assertEquals(7003, TarantinoConfig.from(Map.of(), TarantinoService.QUERY).port());
        assertEquals(7004, TarantinoConfig.from(Map.of(), TarantinoService.CONTROL).port());
    }

    @Test
    void readsTheVariablesTrimmedAndTreatsBlankOnesAsUnset() {
        TarantinoConfig config = TarantinoConfig.from(Map.of(
                "TARANTINO_HAZELCAST", " hazelcast:5701 , backup:5701 ",
                "TARANTINO_HTTP_THREADS", " 16 ",
                "TARANTINO_MIRROR", "/mirror",
                "TARANTINO_CANDIDATES", "mirror",
                "TARANTINO_CORS_ORIGIN", " "), TarantinoService.CONTROL);

        assertEquals(List.of("hazelcast:5701", "backup:5701"), config.hazelcastMembers());
        assertEquals(OptionalInt.of(16), config.httpThreads());
        assertEquals(Optional.of(Path.of("/mirror")), config.mirror());
        assertEquals("mirror", config.candidates());
        assertEquals("*", config.corsOrigin());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TARANTINO_PORT", "TARANTINO_HTTP_THREADS", "TARANTINO_PARALLEL_DOWNLOADS",
            "TARANTINO_MAX_ATTEMPTS", "TARANTINO_INDEX_BATCH", "TARANTINO_BATCH_TIMEOUT_SECONDS", "TARANTINO_LEASE_SECONDS",
            "TARANTINO_HEARTBEAT_SECONDS", "TARANTINO_RECONCILE_SECONDS", "TARANTINO_REQUEUE_SECONDS", "TARANTINO_GC_SECONDS",
            "TARANTINO_TMP_MAX_AGE_SECONDS", "TARANTINO_CLEANUP_SECONDS", "TARANTINO_SNAPSHOT_SECONDS",
            "TARANTINO_POLL_MILLIS", "TARANTINO_ORCHESTRATOR_TICK_MILLIS", "TARANTINO_ORCHESTRATOR_DOWNLOAD_WINDOW",
            "TARANTINO_ORCHESTRATOR_INDEX_WINDOW", "TARANTINO_LOCAL_DOWNLOAD_CAPACITY", "TARANTINO_LOCAL_INDEX_CAPACITY"})
    void rejectsAnyNumberThatIsNotAPositiveIntegerNamingItsVariable(String variable) {
        for (String value : List.of("0", "-5", "many", "1.5", "99999999999")) {
            assertInvalid(variable, Map.of(variable, value), TarantinoService.CRAWLER);
        }
    }

    @Test
    void rejectsAnEmptyListOfHazelcastMembers() {
        assertInvalid("TARANTINO_HAZELCAST", Map.of("TARANTINO_HAZELCAST", " , "), TarantinoService.QUERY);
    }

    @Test
    void renewsALeaseAtLeastThreeTimes() {
        TarantinoConfig.from(Map.of("TARANTINO_HEARTBEAT_SECONDS", "20"), TarantinoService.CRAWLER);
        assertInvalid("TARANTINO_HEARTBEAT_SECONDS", Map.of("TARANTINO_HEARTBEAT_SECONDS", "21"), TarantinoService.CRAWLER);
    }

    @Test
    void waitsTenLeasesBeforeDeletingBatchesOrTemporaryFiles() {
        assertInvalid("TARANTINO_GC_SECONDS", Map.of("TARANTINO_GC_SECONDS", "599"), TarantinoService.CONTROL);
        assertInvalid("TARANTINO_TMP_MAX_AGE_SECONDS", Map.of("TARANTINO_TMP_MAX_AGE_SECONDS", "599"), TarantinoService.CONTROL);
    }

    @Test
    void requeuesOnlyAfterABatchCouldHaveClosed() {
        assertInvalid("TARANTINO_REQUEUE_SECONDS", Map.of("TARANTINO_REQUEUE_SECONDS", "30"), TarantinoService.CONTROL);
    }

    @Test
    void seedsTheMirrorOnlyWhenThereIsOne() {
        assertInvalid("TARANTINO_CANDIDATES", Map.of("TARANTINO_CANDIDATES", "mirror"), TarantinoService.CONTROL);
    }

    @Test
    void crashesOnlyAtAPointOfTheServiceBeingStarted() {
        assertEquals(Optional.of(CrashPoint.AFTER_CLAIM),
                TarantinoConfig.from(Map.of("TARANTINO_CRASH_AT", "after-claim"), TarantinoService.CRAWLER).crashAt());
        assertEquals(Optional.of(CrashPoint.INDEXER_AFTER_DEQUEUE),
                TarantinoConfig.from(Map.of("TARANTINO_CRASH_AT", "after-dequeue"), TarantinoService.INDEXER).crashAt());
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "after-publish"), TarantinoService.CRAWLER);
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "after-dequeue"), TarantinoService.CONTROL);
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "nowhere"), TarantinoService.INDEXER);
    }

    @Test
    void readsTheCoordinationModeInLowerCase() {
        assertEquals(Coordination.POLLING,
                TarantinoConfig.from(Map.of("TARANTINO_COORDINATION", "polling"), TarantinoService.CRAWLER).coordination());
        assertEquals(Coordination.ORCHESTRATOR,
                TarantinoConfig.from(Map.of("TARANTINO_COORDINATION", " orchestrator "), TarantinoService.CONTROL).coordination());
        assertInvalid("TARANTINO_COORDINATION", Map.of("TARANTINO_COORDINATION", "ActiveMQ"), TarantinoService.QUERY);
        assertInvalid("TARANTINO_COORDINATION", Map.of("TARANTINO_COORDINATION", "kafka"), TarantinoService.QUERY);
    }

    @Test
    void usesABrokerOnlyInTheChoreographyModes() {
        assertEquals(List.of(Coordination.HAZELCAST, Coordination.ACTIVEMQ),
                List.of(Coordination.values()).stream().filter(Coordination::usesBroker).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TARANTINO_SNAPSHOT_ON_PUBLISH", "TARANTINO_ACTIVEMQ_PERSISTENT"})
    void acceptsOnlyTrueOrFalseInTheFlags(String variable) {
        for (String value : List.of("yes", "1", "TRUE")) {
            assertInvalid(variable, Map.of(variable, value), TarantinoService.QUERY);
        }
    }

    @Test
    void readsTheFlagsOfTheModes() {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_SNAPSHOT_ON_PUBLISH", "false",
                "TARANTINO_ACTIVEMQ_PERSISTENT", "true", "TARANTINO_POLL_MILLIS", "50"), TarantinoService.QUERY);

        assertFalse(config.snapshotOnPublish());
        assertTrue(config.activeMqPersistent());
        assertEquals(Duration.ofMillis(50), config.poll());
    }

    private static void assertInvalid(String variable, Map<String, String> environment, TarantinoService service) {
        InvalidConfigurationException error = assertThrows(InvalidConfigurationException.class,
                () -> TarantinoConfig.from(environment, service), environment::toString);
        assertEquals(variable, error.variable(), environment::toString);
        assertEquals(variable, error.getMessage().split(" ")[0]);
    }
}
