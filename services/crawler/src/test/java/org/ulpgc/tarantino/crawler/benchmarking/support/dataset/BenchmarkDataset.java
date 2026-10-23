package org.ulpgc.tarantino.crawler.benchmarking.support.dataset;

import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class BenchmarkDataset {

    public static final int NEW_BOOKS = 100;
    private static final int NEW_BOOKS_OFFSET = 1000;

    private final Path cache;
    private final Path bookIds;
    private List<Integer> cachedIds;

    public BenchmarkDataset(Path cache, Path bookIds) {
        this.cache = cache;
        this.bookIds = bookIds;
    }

    public static BenchmarkDataset fromEnvironment() {
        return new BenchmarkDataset(BenchmarkPaths.benchmarks().resolve("cache"), BenchmarkPaths.workload().resolve("book_ids.txt"));
    }

    public List<Integer> ids(int count) {
        return ids(0, count);
    }

    public List<Integer> newIds() {
        return ids(NEW_BOOKS_OFFSET, NEW_BOOKS);
    }

    public List<Integer> ids(int offset, int count) {
        List<Integer> ids = cachedIds();
        if (offset + count > ids.size()) {
            throw new IllegalStateException("The cache holds " + ids.size() + " books but " + (offset + count)
                    + " are needed; run scripts/fill_cache.sh");
        }
        return ids.subList(offset, offset + count);
    }

    public String rawText(int bookId) {
        try {
            return Files.readString(cacheFile(bookId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Integer> cachedIds() {
        if (cachedIds == null) {
            cachedIds = candidateIds().stream().filter(id -> Files.exists(cacheFile(id))).toList();
        }
        return cachedIds;
    }

    private List<Integer> candidateIds() {
        try {
            return Files.readAllLines(bookIds).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path cacheFile(int bookId) {
        return cache.resolve(bookId + ".txt");
    }
}
