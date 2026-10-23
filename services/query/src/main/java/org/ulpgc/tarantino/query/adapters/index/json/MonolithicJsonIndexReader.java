package org.ulpgc.tarantino.query.adapters.index.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class MonolithicJsonIndexReader implements InvertedIndexReader {

    private static final TypeReference<Map<String, Set<Integer>>> INDEX_TYPE = new TypeReference<>() {
    };

    private final Path file;
    private Map<String, Set<Integer>> index;

    public MonolithicJsonIndexReader(Path file) {
        this.file = file;
    }

    @Override
    public Set<Integer> postings(String term) {
        return index().getOrDefault(term, Set.of());
    }

    private Map<String, Set<Integer>> index() {
        if (index == null) {
            index = Files.exists(file) ? storedIndex() : Map.of();
        }
        return index;
    }

    private Map<String, Set<Integer>> storedIndex() {
        try {
            return new ObjectMapper().readValue(file.toFile(), INDEX_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
