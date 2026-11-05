package org.ulpgc.tarantino.bench.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The texts of {@code benchmarks/cache/}, one {@code <book_id>.txt} per book of the corpus (SPEC §9). */
public class CorpusCache {

    private static final Pattern TEXT_NAME = Pattern.compile("([1-9][0-9]*)\\.txt");

    private final Path directory;

    public CorpusCache(Path directory) {
        this.directory = directory;
    }

    /** Each book's text by ascending id; any other file, such as {@code skipped.txt}, is not part of the corpus. */
    public SortedMap<Integer, Path> texts() {
        try (Stream<Path> files = Files.list(directory)) {
            return files.flatMap(file -> bookId(file).map(bookId -> Map.entry(bookId, file)).stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<Integer> bookId(Path file) {
        Matcher name = TEXT_NAME.matcher(file.getFileName().toString());
        return name.matches() ? Optional.of(Integer.parseInt(name.group(1))) : Optional.empty();
    }
}
