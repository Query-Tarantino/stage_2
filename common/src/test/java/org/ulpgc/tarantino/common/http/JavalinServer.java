package org.ulpgc.tarantino.common.http;

import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

final class JavalinServer implements AutoCloseable {

    private final Javalin app;
    private final HttpClient client = HttpClient.newHttpClient();

    JavalinServer(Consumer<Javalin> routes) {
        this.app = Javalin.create();
        routes.accept(app);
        app.start(0);
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() {
        app.stop();
        client.close();
    }
}
