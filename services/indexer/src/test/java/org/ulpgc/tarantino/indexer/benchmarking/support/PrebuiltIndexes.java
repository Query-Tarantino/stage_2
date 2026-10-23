package org.ulpgc.tarantino.indexer.benchmarking.support;

import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Indexes of the first N books, built once per benchmark run and shared by every process of the benchmarks
 * that only need them as a starting point. Their content is deterministic, so reusing them changes no result.
 */
public final class PrebuiltIndexes {

    private static final String PREFIX = "prebuilt-";
    private static final String BUILT_MARKER = ".built";
    private static final String WORKING_MARKER = ".working";

    private PrebuiltIndexes() {
    }

    public static BenchmarkStore of(String index, int books, IndexFixture fixture) {
        BenchmarkStore store = builtStore(index, books);
        Path marker = marker(index, books, BUILT_MARKER);
        if (!Files.exists(marker)) {
            store.clear();
            fixture.index(store.invertedIndex(), fixture.dataset().ids(books));
            create(marker);
        }
        return store;
    }

    /**
     * A copy of a prebuilt index for benchmarks that update it, copied once per run and shared by every process
     * and benchmark method, which put it back to the prebuilt index before each run (SPEC §11) instead of copying
     * it whole: for folders that is hundreds of thousands of files.
     */
    public static BenchmarkStore workingCopyOf(BenchmarkStore prebuilt, String index, int books) {
        BenchmarkStore store = workingStore(index, books);
        Path marker = marker(index, books, WORKING_MARKER);
        if (!Files.exists(marker)) {
            prebuilt.copyTo(store);
            create(marker);
        }
        return store;
    }

    public static Path file(String name) {
        return BenchmarkPaths.scratch(PREFIX + name);
    }

    public static void deleteAll() {
        prebuiltEntries().forEach(PrebuiltIndexes::deleteStoreOf);
        prebuiltEntries().forEach(Directories::delete);
    }

    private static BenchmarkStore builtStore(String index, int books) {
        return BenchmarkStore.forIndex(index, PREFIX + "index-" + index + "-" + books);
    }

    private static BenchmarkStore workingStore(String index, int books) {
        return BenchmarkStore.forIndex(index, PREFIX + "working-" + index + "-" + books);
    }

    private static Path marker(String index, int books, String kind) {
        return file(index + "-" + books + kind);
    }

    private static void deleteStoreOf(Path entry) {
        String name = entry.getFileName().toString();
        for (String kind : List.of(BUILT_MARKER, WORKING_MARKER)) {
            if (name.endsWith(kind)) {
                String[] indexAndBooks = name.substring(PREFIX.length(), name.length() - kind.length()).split("-");
                String index = indexAndBooks[0];
                int books = Integer.parseInt(indexAndBooks[1]);
                (BUILT_MARKER.equals(kind) ? builtStore(index, books) : workingStore(index, books)).clear();
            }
        }
    }

    private static Stream<Path> prebuiltEntries() {
        Path root = BenchmarkPaths.scratchRoot();
        if (!Files.isDirectory(root)) {
            return Stream.empty();
        }
        try (Stream<Path> entries = Files.list(root)) {
            List<Path> prebuilt = entries.filter(entry -> entry.getFileName().toString().startsWith(PREFIX)).toList();
            return prebuilt.stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void create(Path marker) {
        try {
            Files.createDirectories(marker.getParent());
            Files.createFile(marker);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
