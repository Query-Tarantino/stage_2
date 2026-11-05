package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoService;

import java.io.IOException;
import java.util.Locale;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.ulpgc.tarantino.checks.ApiNames.HEALTH;
import static org.ulpgc.tarantino.checks.ApiNames.STATUS;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/**
 * {@code GET /health} of each service on its own port (SPEC §6.5): from the host when the port is published, and
 * otherwise inside its container with {@code docker compose exec}, because only nginx publishes ports (SPEC §6.1).
 */
final class HealthChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);

    private final CheckedClient client;
    private final InsideCompose insideCompose;

    HealthChecks(CheckedClient client) {
        this.client = client;
        this.insideCompose = new InsideCompose(client);
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking /health on service ports");
        for (TarantinoService service : TarantinoService.values()) {
            String name = name(service);
            CheckedClient.Response response = health(service);
            assertTrue(response.status() == HTTP_OK || response.status() == HTTP_UNAVAILABLE, "/health of " + name + " answers 200 or 503");
            assertTrue(response.json() != null && response.json().has(STATUS), "/health of " + name + " has a status");
        }
    }

    private CheckedClient.Response health(TarantinoService service) throws IOException, InterruptedException {
        try {
            return client.get(localUrl(service));
        } catch (IOException exception) {
            return healthInsideContainer(service);
        }
    }

    private CheckedClient.Response healthInsideContainer(TarantinoService service) throws IOException, InterruptedException {
        return insideCompose.get(name(service), localUrl(service), HEALTH);
    }

    private static String localUrl(TarantinoService service) {
        return "http://localhost:" + service.defaultPort() + HEALTH;
    }

    private static String name(TarantinoService service) {
        return service.name().toLowerCase(Locale.ROOT);
    }
}
