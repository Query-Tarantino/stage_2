package org.ulpgc.tarantino.common.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.util.List;
import java.util.function.Supplier;

/** {@code GET /health} of SPEC §6.5: plain JSON, not Problem Details, because a dependency down is not an API error. */
public final class Health {

    public static final String MONGO = "mongo";
    public static final String HAZELCAST = "hazelcast";
    public static final String SNAPSHOT = "snapshot";

    private Health() {
    }

    /** {@code down} lists what does not answer right now, empty when the service is up. */
    public static void register(Javalin app, Supplier<List<String>> down) {
        app.get("/health", context -> answer(context, down.get()));
    }

    private static void answer(Context context, List<String> down) {
        if (down.isEmpty()) {
            respond(context, HttpStatus.OK, new Status("UP", null));
            return;
        }
        RetryAfter.add(context);
        respond(context, HttpStatus.SERVICE_UNAVAILABLE, new Status("DOWN", down));
    }

    private static void respond(Context context, HttpStatus status, Status body) {
        context.status(status).contentType(ContentType.APPLICATION_JSON).result(TarantinoJson.write(body));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Status(String status, List<String> down) {
    }
}
