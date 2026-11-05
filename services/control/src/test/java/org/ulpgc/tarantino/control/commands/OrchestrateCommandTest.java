package org.ulpgc.tarantino.control.commands;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.adapters.http.HttpWorkDispatcher;
import org.ulpgc.tarantino.control.model.DispatchedBooks;
import org.ulpgc.tarantino.control.model.Stage;
import org.ulpgc.tarantino.control.ports.WaitingBooks;
import org.ulpgc.tarantino.control.ports.WorkDispatcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrchestrateCommandTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T10:10:00Z");
    private static final Duration REQUEUE = Duration.ofSeconds(10);

    private final Map<Integer, BookStatus> states = new TreeMap<>();
    private final Map<Stage, DispatchedBooks> dispatched = Map.of(Stage.DOWNLOAD, new DispatchedBooks(), Stage.INDEX, new DispatchedBooks());
    private final List<Integer> replicaOne = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> replicaTwo = Collections.synchronizedList(new ArrayList<>());
    private final RecordingTransitionLog log = new RecordingTransitionLog();
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void dispatchesTheOldestQueuedBooksToTwoCrawlerReplicasUntilBothAreFull() throws IOException {
        List.of(1342, 84, 11, 2600, 76).forEach(bookId -> states.put(bookId, BookStatus.QUEUED));

        command(replicasBehindNginx(2), 512).tick();

        assertEquals(List.of(11, 84), replicaOne);
        assertEquals(List.of(76, 1342), replicaTwo);
        assertEquals(Set.of(11, 76, 84, 1342), dispatched.get(Stage.DOWNLOAD).bookIds());
    }

    @Test
    void stopsAStageForTheTickAtTheFirstRefusalAndKeepsTheOtherStageGoing() {
        List.of(1342, 84).forEach(bookId -> states.put(bookId, BookStatus.QUEUED));
        states.put(11, BookStatus.DOWNLOADED);
        List<BookTask> sent = new ArrayList<>();
        WorkDispatcher refusingDownloads = (stage, task) -> {
            sent.add(task);
            return stage == Stage.INDEX;
        };

        command(refusingDownloads, 512).tick();

        assertEquals(List.of(84, 11), sent.stream().map(BookTask::bookId).toList());
        assertEquals(Set.of(11), dispatched.get(Stage.INDEX).bookIds());
    }

    @Test
    void dispatchesAgainAfterTheRequeueWithATransitionLineAndDropsTheClaimedBooks() {
        List.of(1342, 84).forEach(bookId -> states.put(bookId, BookStatus.QUEUED));
        List<Integer> sent = new ArrayList<>();
        OrchestrateCommand command = command((stage, task) -> sent.add(task.bookId()), 512);
        command.tick();
        states.put(84, BookStatus.DOWNLOADING);
        now.set(NOW.plus(REQUEUE).plusSeconds(1));

        command.tick();

        assertEquals(List.of(84, 1342, 1342), sent);
        assertEquals(List.of(Transition.book(1342, BookStatus.QUEUED, BookStatus.QUEUED)), log.transitions());
    }

    @Test
    void neverHasMoreBooksInFlightThanItsWindow() {
        List.of(1342, 84, 11).forEach(bookId -> states.put(bookId, BookStatus.QUEUED));
        List<Integer> sent = new ArrayList<>();
        OrchestrateCommand command = command((stage, task) -> sent.add(task.bookId()), 2);

        command.tick();
        command.tick();

        assertEquals(List.of(11, 84), sent);
    }

    private OrchestrateCommand command(WorkDispatcher dispatcher, int window) {
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        return new OrchestrateCommand(new InMemoryWaitingBooks(), dispatcher, dispatched,
                Map.of(Stage.DOWNLOAD, window, Stage.INDEX, window), REQUEUE, () -> {
        }, log, clock);
    }

    private WorkDispatcher replicasBehindNginx(int capacityEach) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/download", exchange -> {
            int bookId = TarantinoJson.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), BookTask.class).bookId();
            List<Integer> leastBusy = replicaOne.size() <= replicaTwo.size() ? replicaOne : replicaTwo;
            boolean accepted = leastBusy.size() < capacityEach;
            if (accepted) {
                leastBusy.add(bookId);
            }
            exchange.sendResponseHeaders(accepted ? 202 : 503, -1);
            exchange.close();
        });
        server.start();
        return new HttpWorkDispatcher("http://localhost:" + server.getAddress().getPort());
    }

    private final class InMemoryWaitingBooks implements WaitingBooks {

        @Override
        public Map<Integer, BookStatus> statuses(Collection<Integer> bookIds) {
            Map<Integer, BookStatus> found = new TreeMap<>();
            bookIds.stream().filter(states::containsKey).forEach(bookId -> found.put(bookId, states.get(bookId)));
            return found;
        }

        @Override
        public List<BookTask> oldest(BookStatus status, Collection<Integer> excluded, int limit) {
            return states.entrySet().stream()
                    .filter(state -> state.getValue() == status && !excluded.contains(state.getKey()))
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.naturalOrder())
                    .limit(limit)
                    .map(bookId -> new BookTask(bookId, ENQUEUED_AT))
                    .toList();
        }
    }
}
