package org.ulpgc.tarantino.bench.recovery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpStackTest {

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private HttpServer frontEnd;
    private HttpServer operations;
    private HttpStack stack;

    @BeforeEach
    void startNginxStandIns() throws IOException {
        frontEnd = server();
        frontEnd.createContext("/books/", exchange -> answer(exchange, 404, "{\"status\":404,\"code\":\"BOOK_NOT_FOUND\"}"));
        frontEnd.createContext("/search", exchange -> answer(exchange, 200, "{\"total\":1,\"results\":[{\"book_id\":1342}]}"));
        operations = server();
        operations.createContext("/control/status", exchange -> answer(exchange, 200, "{\"generation\":1}"));
        stack = new HttpStack(url(frontEnd), url(operations));
    }

    @AfterEach
    void stop() {
        frontEnd.stop(0);
        operations.stop(0);
    }

    @Test
    void asksControlOnTheOperationsPort() {
        Answer status = stack.controlStatus();

        assertTrue(status.ok());
        assertEquals(1, status.body().get("generation").asInt());
    }

    @Test
    void asksBooksAndSearchesOnTheFrontEndPortEncodingTheQuery() {
        Answer book = stack.book(84);
        Answer search = stack.search("pride & prejudice", 100, 100);

        assertFalse(book.ok());
        assertEquals(404, book.status());
        assertEquals(1342, search.body().get("results").get(0).get("book_id").asInt());
        assertEquals(List.of("/books/84", "/search?q=pride%20%26%20prejudice&offset=100&limit=100"), asked);
    }

    @Test
    void throwsWhenNothingAnswers() {
        frontEnd.stop(0);

        assertThrows(UncheckedIOException.class, () -> stack.book(84));
    }

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        return server;
    }

    private void answer(HttpExchange exchange, int status, String json) throws IOException {
        asked.add(exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
