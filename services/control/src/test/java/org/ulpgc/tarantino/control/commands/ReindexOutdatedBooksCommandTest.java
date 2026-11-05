package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.ports.OutdatedBooks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReindexOutdatedBooksCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:00Z");
    private static final int WRITE_GENERATION = 2;

    private final BooksOfGenerationOne books = new BooksOfGenerationOne();
    private final List<BookTask> queue = new ArrayList<>();
    private final RecordingTransitionLog log = new RecordingTransitionLog();

    @BeforeEach
    void reindexTwoBooksOneOfThemAlreadyReindexed() {
        books.outdate(1342, true);
        books.outdate(84, false);
        new ReindexOutdatedBooksCommand(books, queue::add, log, Clock.fixed(NOW, ZoneOffset.UTC)).execute();
    }

    @Test
    void enqueuesOnlyTheBooksItReindexed() {
        assertEquals(List.of(new BookTask(1342, NOW)), queue);
    }

    @Test
    void logsOnlyTheReindexesThatMatched() {
        assertEquals(List.of(Transition.book(1342, BookStatus.PUBLISHED, BookStatus.DOWNLOADED)), log.transitions());
    }

    @Test
    void reindexesIntoTheWriteGeneration() {
        assertEquals(List.of(WRITE_GENERATION, WRITE_GENERATION), books.reindexedInto());
    }

    private static final class BooksOfGenerationOne implements OutdatedBooks {

        private final Map<Integer, Boolean> outcomes = new LinkedHashMap<>();
        private final List<Integer> reindexedInto = new ArrayList<>();

        void outdate(int bookId, boolean reindexed) {
            outcomes.put(bookId, reindexed);
        }

        List<Integer> reindexedInto() {
            return reindexedInto;
        }

        @Override
        public int writeGeneration() {
            return WRITE_GENERATION;
        }

        @Override
        public List<Integer> outdated(int writeGeneration) {
            return writeGeneration == WRITE_GENERATION ? List.copyOf(outcomes.keySet()) : List.of();
        }

        @Override
        public boolean reindex(int bookId, int writeGeneration) {
            reindexedInto.add(writeGeneration);
            return outcomes.get(bookId);
        }
    }
}
