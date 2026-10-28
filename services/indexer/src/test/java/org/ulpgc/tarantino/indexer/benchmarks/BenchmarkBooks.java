package org.ulpgc.tarantino.indexer.benchmarks;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Books of the local mirror ({@code TARANTINO_MIRROR}, or {@code benchmarks/mirror}), split into header and body as
 * {@code crawler} leaves them in the datalake, so the benchmarks run on the corpus that {@code workload/corpus.sha256}
 * pins (SPEC §9).
 */
final class BenchmarkBooks {

    private static final Pattern START_MARKER = Pattern.compile("\\*\\*\\* ?START OF[^\\n]*\\n");
    private static final Pattern END_MARKER = Pattern.compile("\\*\\*\\* ?END OF");
    private static final Path DEFAULT_MIRROR = Path.of("benchmarks", "mirror");
    private static final TarantinoConfig CONFIG = TarantinoConfig.fromEnvironment(TarantinoService.INDEXER);

    private BenchmarkBooks() {
    }

    static BookText book(int bookId) {
        String text = read(mirrorFile(bookId));
        Matcher start = START_MARKER.matcher(text);
        Matcher end = END_MARKER.matcher(text);
        if (!start.find() || !end.find(start.end())) {
            throw new IllegalStateException("Book " + bookId + " of the mirror has no Gutenberg markers");
        }
        return new BookText(bookId, text.substring(0, start.start()), text.substring(start.end(), end.start()));
    }

    /** The first {@code count} books of {@code workload/book_ids.txt} that the mirror holds, in that order. */
    static List<BookText> firstBooks(int count) {
        return read(CONFIG.workload().resolve("book_ids.txt")).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(Integer::valueOf)
                .filter(bookId -> Files.exists(mirrorFile(bookId)))
                .limit(count)
                .map(BenchmarkBooks::book)
                .toList();
    }

    static Tokenizer tokenizer() {
        return new Tokenizer(new FileStopwordsLoader(CONFIG.workload().resolve("stopwords.txt")).stopwords());
    }

    private static Path mirrorFile(int bookId) {
        return CONFIG.mirror().orElse(DEFAULT_MIRROR).resolve(String.valueOf(bookId)).resolve("pg" + bookId + ".txt");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
