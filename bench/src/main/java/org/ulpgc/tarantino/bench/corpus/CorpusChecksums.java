package org.ulpgc.tarantino.bench.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The id and SHA-256 of each text of the corpus, as {@code workload/corpus.sha256} keeps them (SPEC §9): one
 * {@code <book_id> <sha256>} line per book, by ascending id.
 */
public record CorpusChecksums(Map<Integer, String> sha256ByBookId) {

    private static final String SEPARATOR = " ";
    private static final String LINE_END = "\n";

    public CorpusChecksums {
        sha256ByBookId = Collections.unmodifiableSortedMap(new TreeMap<>(sha256ByBookId));
    }

    public static CorpusChecksums of(CorpusCache cache) {
        return new CorpusChecksums(cache.texts().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, text -> Sha256.hex(bytes(text.getValue())))));
    }

    public static CorpusChecksums parse(String content) {
        return new CorpusChecksums(content.lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.strip().split(SEPARATOR))
                .collect(Collectors.toMap(fields -> Integer.parseInt(fields[0]), fields -> fields[1])));
    }

    public String render() {
        return sha256ByBookId.entrySet().stream()
                .map(book -> book.getKey() + SEPARATOR + book.getValue() + LINE_END)
                .collect(Collectors.joining());
    }

    /** The SHA-256 of {@link #render()}: the same for two runs only if every text is the same. */
    public String combinedSha256() {
        return Sha256.hex(render().getBytes(StandardCharsets.UTF_8));
    }

    public List<Integer> bookIds() {
        return List.copyOf(sha256ByBookId.keySet());
    }

    private static byte[] bytes(Path text) {
        try {
            return Files.readAllBytes(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
