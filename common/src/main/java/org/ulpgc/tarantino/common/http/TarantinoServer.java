package org.ulpgc.tarantino.common.http;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.json.JavalinJackson;
import io.javalin.util.ConcurrencyUtil;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.util.List;
import java.util.function.Consumer;

/**
 * The HTTP server of every service: its {@code TARANTINO_PORT} and {@code TARANTINO_HTTP_THREADS} (SPEC §4.7), the
 * {@code TarantinoJson} mapper, Problem Details and {@code /health}. Without {@code TARANTINO_HTTP_THREADS} it keeps
 * Javalin's own pool, from 8 to 250 threads.
 */
public final class TarantinoServer {

    private static final String THREAD_POOL_NAME = "JettyServerThreadPool";
    private static final int JAVALIN_MIN_THREADS = 8;

    private TarantinoServer() {
    }

    public static Javalin start(TarantinoConfig config, List<Dependency> dependencies, Consumer<Javalin> routes) {
        Javalin app = Javalin.create(javalin -> configure(javalin, config));
        ProblemDetails.register(app);
        Health.register(app, () -> down(dependencies));
        routes.accept(app);
        return app.start(config.port());
    }

    private static void configure(JavalinConfig javalin, TarantinoConfig config) {
        javalin.showJavalinBanner = false;
        javalin.jsonMapper(new JavalinJackson(TarantinoJson.mapper(), false));
        config.httpThreads().ifPresent(threads -> javalin.jetty.threadPool =
                ConcurrencyUtil.jettyThreadPool(THREAD_POOL_NAME, Math.min(JAVALIN_MIN_THREADS, threads), threads, false));
    }

    private static List<String> down(List<Dependency> dependencies) {
        return dependencies.stream()
                .filter(dependency -> !dependency.answers().getAsBoolean())
                .map(Dependency::name)
                .toList();
    }
}
