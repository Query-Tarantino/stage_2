package org.ulpgc.tarantino.indexer.adapters.index.folders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermFileRestoreTest {

    @TempDir
    Path directory;

    @Test
    void undoesAnUpdateByRestoringOnlyTheTouchedTerms() throws IOException {
        Path snapshot = directory.resolve("snapshot");
        Path index = directory.resolve("index");
        indexBook(snapshot, 1, "island", "whale");
        indexBook(index, 1, "island", "whale");
        indexBook(index, 2, "island", "écume");

        TermFileRestore.restore(snapshot, index, Set.of("island", "écume"));

        assertEquals(files(snapshot), files(index));
    }

    private static void indexBook(Path root, int bookId, String... terms) {
        FolderPerTermIndexAdapter index = new FolderPerTermIndexAdapter(root);
        Map<String, Integer> frequencies = new TreeMap<>();
        for (String term : terms) {
            frequencies.put(term, 1);
        }
        index.add(new TermOccurrences(bookId, frequencies));
        index.flush();
    }

    private static Map<Path, String> files(Path root) throws IOException {
        Map<Path, String> contents = new TreeMap<>();
        try (Stream<Path> entries = Files.walk(root)) {
            for (Path file : entries.filter(Files::isRegularFile).toList()) {
                contents.put(root.relativize(file), Files.readString(file));
            }
        }
        return contents;
    }
}
