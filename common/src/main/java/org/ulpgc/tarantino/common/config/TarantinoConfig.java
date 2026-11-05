package org.ulpgc.tarantino.common.config;

import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Every {@code TARANTINO_*} variable of SPEC §4.7 with its default, read and validated at startup in every service.
 * Anything invalid throws {@link InvalidConfigurationException} naming the variable, and the service does not start.
 */
public record TarantinoConfig(
        int port,
        OptionalInt httpThreads,
        String mongoUri,
        List<String> hazelcastMembers,
        String hazelcastCluster,
        Path datalake,
        Path workload,
        Optional<Path> mirror,
        String candidates,
        int parallelDownloads,
        int maxAttempts,
        int indexBatch,
        Duration batchTimeout,
        Duration lease,
        Duration heartbeat,
        Duration reconcile,
        Duration requeue,
        Duration gc,
        Duration tmpMaxAge,
        Duration cleanup,
        Duration snapshot,
        String corsOrigin,
        Optional<CrashPoint> crashAt,
        Coordination coordination,
        boolean snapshotOnPublish,
        String activeMq,
        boolean activeMqPersistent,
        Duration poll,
        Duration orchestratorTick,
        int orchestratorDownloadWindow,
        int orchestratorIndexWindow,
        String orchestratorTarget,
        int localDownloadCapacity,
        int localIndexCapacity) {

    /** The {@code TARANTINO_CANDIDATES} value that seeds every book of the local mirror. */
    public static final String MIRROR_CANDIDATES = "mirror";
    /** The Mongo URI variable, which {@code bench explain} also reads, and its default. */
    public static final String MONGO_URI = "TARANTINO_MONGO_URI";
    public static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    /** The snapshot refresh variable, which {@code checks} also reads to time its waits, and its default. */
    public static final String SNAPSHOT_SECONDS = "TARANTINO_SNAPSHOT_SECONDS";
    public static final int DEFAULT_SNAPSHOT_SECONDS = 5;

    private static final int MIN_LEASES_BEFORE_DELETING = 10;
    private static final int MIN_HEARTBEATS_PER_LEASE = 3;

    public static TarantinoConfig fromEnvironment(TarantinoService service) {
        return from(System.getenv(), service);
    }

    public static TarantinoConfig from(Map<String, String> environment, TarantinoService service) {
        Variables variables = new Variables(environment);
        TarantinoConfig config = new TarantinoConfig(
                variables.positive("TARANTINO_PORT", service.defaultPort()),
                variables.optionalPositive("TARANTINO_HTTP_THREADS"),
                variables.text(MONGO_URI, DEFAULT_MONGO_URI),
                variables.list("TARANTINO_HAZELCAST", "localhost:5701"),
                variables.text("TARANTINO_HAZELCAST_CLUSTER", HazelcastContracts.DEFAULT_CLUSTER),
                Path.of(variables.text("TARANTINO_DATALAKE", "datalake")),
                Path.of(variables.text("TARANTINO_WORKLOAD", "workload")),
                variables.optionalText("TARANTINO_MIRROR").map(Path::of),
                variables.text("TARANTINO_CANDIDATES", "sample_ids.txt"),
                variables.positive("TARANTINO_PARALLEL_DOWNLOADS", 8),
                variables.positive("TARANTINO_MAX_ATTEMPTS", 3),
                variables.positive("TARANTINO_INDEX_BATCH", 100),
                variables.seconds("TARANTINO_BATCH_TIMEOUT_SECONDS", 30),
                variables.seconds("TARANTINO_LEASE_SECONDS", 60),
                variables.seconds("TARANTINO_HEARTBEAT_SECONDS", 20),
                variables.seconds("TARANTINO_RECONCILE_SECONDS", 30),
                variables.seconds("TARANTINO_REQUEUE_SECONDS", 120),
                variables.seconds("TARANTINO_GC_SECONDS", 600),
                variables.seconds("TARANTINO_TMP_MAX_AGE_SECONDS", 600),
                variables.seconds("TARANTINO_CLEANUP_SECONDS", 3600),
                variables.seconds(SNAPSHOT_SECONDS, DEFAULT_SNAPSHOT_SECONDS),
                variables.text("TARANTINO_CORS_ORIGIN", "*"),
                variables.optionalText("TARANTINO_CRASH_AT").map(point -> crashPoint(service, point)),
                coordination(variables.text("TARANTINO_COORDINATION", Coordination.HAZELCAST.variableValue())),
                variables.bool("TARANTINO_SNAPSHOT_ON_PUBLISH", true),
                variables.text("TARANTINO_ACTIVEMQ", "failover:(tcp://localhost:61616)?timeout=1000"),
                variables.bool("TARANTINO_ACTIVEMQ_PERSISTENT", false),
                variables.millis("TARANTINO_POLL_MILLIS", 200),
                variables.millis("TARANTINO_ORCHESTRATOR_TICK_MILLIS", 200),
                variables.positive("TARANTINO_ORCHESTRATOR_DOWNLOAD_WINDOW", 512),
                variables.positive("TARANTINO_ORCHESTRATOR_INDEX_WINDOW", 400),
                variables.text("TARANTINO_ORCHESTRATOR_TARGET", "http://nginx:8082"),
                variables.positive("TARANTINO_LOCAL_DOWNLOAD_CAPACITY", 256),
                variables.positive("TARANTINO_LOCAL_INDEX_CAPACITY", 200));
        config.checkInvariants();
        return config;
    }

    private void checkInvariants() {
        require(heartbeat.multipliedBy(MIN_HEARTBEATS_PER_LEASE).compareTo(lease) <= 0,
                "TARANTINO_HEARTBEAT_SECONDS", "must be at most TARANTINO_LEASE_SECONDS / 3");
        require(gc.compareTo(lease.multipliedBy(MIN_LEASES_BEFORE_DELETING)) >= 0,
                "TARANTINO_GC_SECONDS", "must be at least 10 × TARANTINO_LEASE_SECONDS");
        require(tmpMaxAge.compareTo(lease.multipliedBy(MIN_LEASES_BEFORE_DELETING)) >= 0,
                "TARANTINO_TMP_MAX_AGE_SECONDS", "must be at least 10 × TARANTINO_LEASE_SECONDS");
        require(requeue.compareTo(batchTimeout) > 0,
                "TARANTINO_REQUEUE_SECONDS", "must be greater than TARANTINO_BATCH_TIMEOUT_SECONDS");
        require(!MIRROR_CANDIDATES.equals(candidates) || mirror.isPresent(),
                "TARANTINO_CANDIDATES", "can only be mirror with TARANTINO_MIRROR set");
    }

    private static void require(boolean holds, String variable, String reason) {
        if (!holds) {
            throw new InvalidConfigurationException(variable, reason);
        }
    }

    private static Coordination coordination(String value) {
        return Coordination.of(value).orElseThrow(() -> new InvalidConfigurationException("TARANTINO_COORDINATION",
                "must be hazelcast, activemq, polling or orchestrator, not " + value));
    }

    private static CrashPoint crashPoint(TarantinoService service, String point) {
        return CrashPoint.of(service, point).orElseThrow(() -> new InvalidConfigurationException("TARANTINO_CRASH_AT",
                "must be a point of " + service.name().toLowerCase(Locale.ROOT) + " in SPEC §8, not " + point));
    }
}
