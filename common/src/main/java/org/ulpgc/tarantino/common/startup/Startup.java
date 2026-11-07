package org.ulpgc.tarantino.common.startup;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.http.Dependency;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.mongo.IndexState;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * The startup every service shares (SPEC §2.4): it opens the HTTP port first, then retries Mongo and the broker of its
 * mode, if it has one, without limit, so the startup order does not matter. Until they answer, {@code /health} answers {@code 503} and the
 * service consumes no queues, because {@link #start} has not returned yet.
 */
public final class Startup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Startup.class);
    private static final Duration RETRY_PAUSE = Duration.ofSeconds(2);
    private static final int FAILED_TO_START = 1;

    private Startup() {
    }

    /**
     * Opens the HTTP port, creates the generation pointer (SPEC §5.1) and waits for the broker, if there is one. From here
     * on, an exception that leaves the calling thread, the service's {@code main}, ends the process.
     */
    public static void start(TarantinoConfig config, Connections connections, List<Dependency> serviceDependencies,
                             Consumer<Javalin> routes) {
        exitIfThisThreadFails(Runtime.getRuntime()::halt);
        List<Dependency> dependencies = Stream.concat(connections.dependencies().stream(), serviceDependencies.stream()).toList();
        TarantinoServer.start(config, dependencies, routes);
        retry("create the generation pointer", () -> new IndexState(connections.database()).createPointer());
        connections.broker().ifPresent(broker -> waitFor(broker.name(), broker::connected));
    }

    /**
     * Exits with code {@value #FAILED_TO_START} if the calling thread dies with an exception, such as a missing
     * {@code stopwords.txt} after the port is open. The HTTP threads would otherwise keep the JVM alive with
     * {@code /health} answering {@code UP} and no worker or reconciliation behind it (SPEC §2.4).
     */
    static void exitIfThisThreadFails(IntConsumer exit) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
            LOGGER.error("Could not start, so the service exits", error);
            exit.accept(FAILED_TO_START);
        });
    }

    /** Retries a startup step against Mongo until it works. */
    public static void retry(String step, Runnable action) {
        retry(step, action, RETRY_PAUSE);
    }

    static void retry(String step, Runnable action, Duration pause) {
        while (true) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                LOGGER.warn("Could not {} yet, retrying in {} ms: {}", step, pause.toMillis(), e.getMessage());
                pause(pause);
            }
        }
    }

    private static void waitFor(String dependency, BooleanSupplier answers) {
        if (!answers.getAsBoolean()) {
            LOGGER.info("Waiting for {}", dependency);
        }
        while (!answers.getAsBoolean()) {
            pause(RETRY_PAUSE);
        }
    }

    private static void pause(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting", e);
        }
    }
}
