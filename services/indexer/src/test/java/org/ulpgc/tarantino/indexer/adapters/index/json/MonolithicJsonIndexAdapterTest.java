package org.ulpgc.tarantino.indexer.adapters.index.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonolithicJsonIndexAdapterTest {

    @TempDir
    Path directory;

    @Test
    void writesSortedTermsWithSortedUniquePostings() throws IOException {
        Path file = directory.resolve("datamarts/inverted_index.json");
        MonolithicJsonIndexAdapter index = new MonolithicJsonIndexAdapter(file);
        index.add(new TermOccurrences(1342, Map.of("whale", 1, "island", 2)));
        index.add(new TermOccurrences(5, Map.of("island", 1)));
        index.add(new TermOccurrences(5, Map.of("island", 1)));
        index.flush();

        assertEquals("{\"island\":[5,1342],\"whale\":[1342]}", Files.readString(file));
    }

    @Test
    void mergesNewBooksIntoAnExistingIndexFile() throws IOException {
        Path file = directory.resolve("inverted_index.json");
        Files.writeString(file, "{\"island\":[5]}");

        MonolithicJsonIndexAdapter index = new MonolithicJsonIndexAdapter(file);
        index.add(new TermOccurrences(1342, Map.of("island", 1)));
        index.flush();

        assertEquals("{\"island\":[5,1342]}", Files.readString(file));
    }

    @Test
    void opensTheStoredIndexIntoMemoryBeforeAddingBooks() throws IOException {
        Path file = directory.resolve("inverted_index.json");
        Files.writeString(file, "{\"island\":[5]}");
        MonolithicJsonIndexAdapter index = new MonolithicJsonIndexAdapter(file);

        index.open();
        Files.delete(file);
        index.add(new TermOccurrences(1342, Map.of("whale", 1)));
        index.flush();

        assertEquals("{\"island\":[5],\"whale\":[1342]}", Files.readString(file));
    }
}
