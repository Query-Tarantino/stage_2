package org.ulpgc.tarantino.common.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthTest {

    @Test
    void answersUpWhenEverythingAnswers() throws IOException, InterruptedException {
        try (JavalinServer server = new JavalinServer(app -> Health.register(app, List::of))) {
            HttpResponse<String> response = server.get("/health");

            assertEquals(200, response.statusCode());
            assertEquals(Optional.of("application/json"), response.headers().firstValue("Content-Type"));
            assertEquals("{\"status\":\"UP\"}", response.body());
        }
    }

    @Test
    void listsWhatIsDownAndAsksToRetry() throws IOException, InterruptedException {
        try (JavalinServer server = new JavalinServer(app -> Health.register(app, () -> List.of(Health.MONGO)))) {
            HttpResponse<String> response = server.get("/health");

            assertEquals(503, response.statusCode());
            assertEquals(Optional.of("5"), response.headers().firstValue("Retry-After"));
            assertEquals(Optional.of("application/json"), response.headers().firstValue("Content-Type"));
            assertEquals("{\"status\":\"DOWN\",\"down\":[\"mongo\"]}", response.body());
        }
    }
}
