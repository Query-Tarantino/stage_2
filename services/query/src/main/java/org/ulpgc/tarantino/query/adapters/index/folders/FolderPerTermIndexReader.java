package org.ulpgc.tarantino.query.adapters.index.folders;

import org.ulpgc.tarantino.query.ports.InvertedIndexReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FolderPerTermIndexReader implements InvertedIndexReader {

    private final Path root;

    public FolderPerTermIndexReader(Path root) {
        this.root = root;
    }

    @Override
    public Set<Integer> postings(String term) {
        return lines(TermFiles.file(root, term)).stream().map(Integer::valueOf).collect(Collectors.toSet());
    }

    private static List<String> lines(Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
