package org.ulpgc.tarantino.common.terms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class FileStopwordsLoader {

    private final Path file;

    public FileStopwordsLoader(Path file) {
        this.file = file;
    }

    public Set<String> stopwords() {
        return lines().stream()
                .map(line -> line.strip().toLowerCase(Locale.ROOT))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<String> lines() {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
