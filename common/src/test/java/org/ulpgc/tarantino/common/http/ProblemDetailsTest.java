package org.ulpgc.tarantino.common.http;

import com.hazelcast.client.HazelcastClientOfflineException;
import com.mongodb.MongoTimeoutException;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemDetailsTest {

    private JavalinServer server;

    @BeforeEach
    void start() {
        server = new JavalinServer(app -> {
            ProblemDetails.register(app);
            routes(app);
        });
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void answersABadParameterNamingIt() throws IOException, InterruptedException {
        HttpResponse<String> response = server.get("/limit");

        assertEquals(400, response.statusCode());
        assertEquals(Optional.of(ProblemDetails.CONTENT_TYPE), response.headers().firstValue("Content-Type"));
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"code\":\"INVALID_PARAMETER\","
                + "\"parameter\":\"limit\",\"detail\":\"limit must be between 1 and 100\"}", response.body());
    }

    @Test
    void namesTheParameterOnlyInBadRequests() throws IOException, InterruptedException {
        HttpResponse<String> response = server.get("/book");

        assertEquals(404, response.statusCode());
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"code\":\"BOOK_NOT_FOUND\","
                + "\"detail\":\"Book 84 not found\"}", response.body());
    }

    @Test
    void asksToRetryWhenADependencyIsUnavailable() throws IOException, InterruptedException {
        HttpResponse<String> response = server.get("/mongo");

        assertEquals(503, response.statusCode());
        assertEquals(Optional.of("5"), response.headers().firstValue("Retry-After"));
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                + "\"code\":\"DEPENDENCY_UNAVAILABLE\",\"detail\":\"MongoDB does not answer\"}", response.body());
    }

    @Test
    void answersMongoOrHazelcastNotAnsweringAsUnavailable() throws IOException, InterruptedException {
        HttpResponse<String> mongo = server.get("/mongo-timeout");
        HttpResponse<String> hazelcast = server.get("/hazelcast-offline");

        assertEquals(503, mongo.statusCode());
        assertEquals(Optional.of("5"), mongo.headers().firstValue("Retry-After"));
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                + "\"code\":\"DEPENDENCY_UNAVAILABLE\",\"detail\":\"MongoDB does not answer\"}", mongo.body());
        assertEquals(503, hazelcast.statusCode());
        assertEquals(Optional.of("5"), hazelcast.headers().firstValue("Retry-After"));
    }

    @Test
    void answersAnythingUnexpectedWithoutCode() throws IOException, InterruptedException {
        HttpResponse<String> response = server.get("/bug");

        assertEquals(500, response.statusCode());
        assertEquals(Optional.of(ProblemDetails.CONTENT_TYPE), response.headers().firstValue("Content-Type"));
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500}", response.body());
    }

    private static void routes(Javalin app) {
        app.get("/limit", context -> {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, "limit", "limit must be between 1 and 100");
        });
        app.get("/book", context -> {
            throw new ProblemException(ProblemCode.BOOK_NOT_FOUND, "book_id", "Book 84 not found");
        });
        app.get("/mongo-timeout", context -> {
            throw new MongoTimeoutException("Timed out waiting for a server");
        });
        app.get("/hazelcast-offline", context -> {
            throw new HazelcastClientOfflineException();
        });
        app.get("/mongo", context -> {
            throw new ProblemException(ProblemCode.DEPENDENCY_UNAVAILABLE, "MongoDB does not answer");
        });
        app.get("/bug", context -> {
            throw new IllegalStateException("a bug");
        });
    }
}
