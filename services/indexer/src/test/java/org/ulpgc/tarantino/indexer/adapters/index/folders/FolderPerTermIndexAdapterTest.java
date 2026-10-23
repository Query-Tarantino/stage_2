package org.ulpgc.tarantino.indexer.adapters.index.folders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FolderPerTermIndexAdapterTest {

    @TempDir
    Path root;

    @Test
    void mergesSortedUniquePostingsIntoOneFilePerTerm() throws IOException {
        FolderPerTermIndexAdapter index = new FolderPerTermIndexAdapter(root);
        index.add(new TermOccurrences(1342, Map.of("island", 3, "écume", 1)));
        index.flush();
        index.add(new TermOccurrences(5, Map.of("island", 1)));
        index.add(new TermOccurrences(1342, Map.of("island", 3)));
        index.flush();

        assertEquals("5\n1342\n", Files.readString(root.resolve("i/island.txt")));
        assertEquals("1342\n", Files.readString(root.resolve("%C3%A9/%C3%A9cume.txt")));
    }

    @Test
    void keepsTermsApartThatCaseOrNormalizationInsensitiveFileSystemsWouldMerge() throws IOException {
        List<String> terms = List.of("shape", "ſhape", "heißt", "heisst", "λόγος", "λόγοσ",
                Normalizer.normalize("café", Normalizer.Form.NFC), Normalizer.normalize("café", Normalizer.Form.NFD));
        FolderPerTermIndexAdapter index = new FolderPerTermIndexAdapter(root);
        for (int bookId = 1; bookId <= terms.size(); bookId++) {
            index.add(new TermOccurrences(bookId, Map.of(terms.get(bookId - 1), 1)));
        }
        index.flush();

        try (Stream<Path> files = Files.walk(root)) {
            assertEquals(terms.size(), files.filter(Files::isRegularFile).count());
        }
    }
}
