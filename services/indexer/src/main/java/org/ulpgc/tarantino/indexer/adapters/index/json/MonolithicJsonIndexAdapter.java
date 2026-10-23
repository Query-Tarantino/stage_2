package org.ulpgc.tarantino.indexer.adapters.index.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeMap;
import java.util.TreeSet;

public class MonolithicJsonIndexAdapter implements InvertedIndexStorage {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<TreeMap<String, TreeSet<Integer>>> INDEX_TYPE = new TypeReference<>() {
    };

    private final Path file;
    private TreeMap<String, TreeSet<Integer>> index;

    public MonolithicJsonIndexAdapter(Path file) {
        this.file = file;
    }

    @Override
    public void open() {
        index();
    }

    @Override
    public void add(TermOccurrences occurrences) {
        occurrences.frequencies().keySet().forEach(term -> postings(term).add(occurrences.bookId()));
    }

    @Override
    public void flush() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            writeAtomically();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TreeSet<Integer> postings(String term) {
        return index().computeIfAbsent(term, key -> new TreeSet<>());
    }

    private TreeMap<String, TreeSet<Integer>> index() {
        if (index == null) {
            index = Files.exists(file) ? storedIndex() : new TreeMap<>();
        }
        return index;
    }

    private TreeMap<String, TreeSet<Integer>> storedIndex() {
        try {
            return MAPPER.readValue(file.toFile(), INDEX_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeAtomically() throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        MAPPER.writeValue(temporary.toFile(), index());
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
