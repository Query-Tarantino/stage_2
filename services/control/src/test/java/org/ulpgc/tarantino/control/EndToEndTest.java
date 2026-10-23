package org.ulpgc.tarantino.control;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.ulpgc.tarantino.control.commands.ControlPipeline;
import org.ulpgc.tarantino.control.model.NextStep.Action;
import org.ulpgc.tarantino.control.model.StepReport;
import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.indexer.IndexerConfig;
import org.ulpgc.tarantino.indexer.adapters.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.query.QueryConfig;
import org.ulpgc.tarantino.query.QueryFactory;
import org.ulpgc.tarantino.query.commands.SearchCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole pipeline over a local mirror of three small books: with no candidates file the control service takes
 * every book of the mirror, three at once, into the datalake and indexes them in batches, and the query service
 * finds them by their words. Downloads finish in any order, so only the batches' sizes are fixed.
 */
class EndToEndTest {

    private static final Map<Integer, String> MIRROR_BOOKS = Map.of(
            11, gutenbergText("Alice's Adventures in Wonderland", "Lewis Carroll", "English",
                    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do."),
            84, gutenbergText("Frankenstein; Or, The Modern Prometheus", "Mary Wollstonecraft Shelley", "English",
                    "You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you"
                            + " have regarded with such evil forebodings. I arrived here yesterday, and my first task is to"
                            + " assure my dear sister of my welfare and increasing confidence in the success of my undertaking."),
            2000, gutenbergText("Don Quijote", "Miguel de Cervantes Saavedra", "Spanish",
                    "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo"
                            + " de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor."));
    private static final int PARALLEL_DOWNLOADS = 3;
    private static final int INDEX_BATCH = 2;
    private static final String UNUSED_MONGO = "mongodb://localhost:27017";

    @RegisterExtension
    static final TemporaryMongoDatabase MONGO = new TemporaryMongoDatabase();

    @TempDir
    Path root;

    @ParameterizedTest(name = "{0} datalake, {1} index, {2} metadata")
    @CsvSource({"time, json, sqlite", "book, folders, sqlite", "batch, mongo, mongo"})
    void downloadsIndexesAndFindsEveryBookOfAMirror(String layout, String index, String metadata) throws IOException {
        String mongoUri = "mongo".equals(index) || "mongo".equals(metadata) ? MONGO.uri() : UNUSED_MONGO;
        Path workload = workload();
        ControlConfig control = new ControlConfig(root.resolve("control"), workload, PARALLEL_DOWNLOADS, INDEX_BATCH);
        CrawlerConfig crawler = new CrawlerConfig(root.resolve("datalake"), layout, mirror());
        ControlPipeline pipeline = ControlFactory.pipeline(control, crawler,
                new IndexerConfig(root.resolve("datalake"), layout, root.resolve("datamarts"), index, metadata, mongoUri, workload),
                ControlFactory.candidates(control, crawler, Optional.empty()));

        List<StepReport> reports = run(pipeline);

        assertTrue(reports.stream().allMatch(report -> report.outcome().succeeded()), reports::toString);
        assertEquals(List.of(11, 84, 2000), books(reports, Action.DOWNLOAD));
        assertEquals(List.of(2, 1), reports.stream().filter(report -> report.step().action() == Action.INDEX)
                .map(report -> report.step().bookIds().size()).toList());
        assertEquals(Set.of("11", "84", "2000"), Set.copyOf(Files.readAllLines(root.resolve("control/indexed_books.txt"))));
        SearchCommand search = QueryFactory.searchCommand(new QueryConfig(root.resolve("datamarts"), index, metadata, mongoUri, workload));
        assertEquals(List.of("11 Alice's Adventures in Wonderland by Lewis Carroll",
                "84 Frankenstein; Or, The Modern Prometheus by Mary Wollstonecraft Shelley"), found(search, "sister"));
        assertEquals(List.of("11 Alice's Adventures in Wonderland by Lewis Carroll"), found(search, "tired sister"));
        assertEquals(List.of("2000 Don Quijote by Miguel de Cervantes Saavedra"), found(search, "rocín"));
        assertEquals(List.of(), found(search, "whale"));
    }

    private Path mirror() throws IOException {
        Path mirror = root.resolve("mirror");
        for (Map.Entry<Integer, String> book : MIRROR_BOOKS.entrySet()) {
            Path file = mirror.resolve(book.getKey().toString()).resolve("pg" + book.getKey() + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, book.getValue());
        }
        return mirror;
    }

    private Path workload() throws IOException {
        Path workload = Files.createDirectories(root.resolve("workload"));
        Files.writeString(workload.resolve("stopwords.txt"), "the\nof\nand\n");
        return workload;
    }

    private static List<StepReport> run(ControlPipeline pipeline) {
        return Stream.generate(pipeline::runStep).takeWhile(report -> !report.idle()).toList();
    }

    private static List<Integer> books(List<StepReport> reports, Action action) {
        return reports.stream()
                .filter(report -> report.step().action() == action)
                .flatMap(report -> report.step().bookIds().stream())
                .sorted()
                .toList();
    }

    private static List<String> found(SearchCommand search, String query) {
        return search.execute(query).books().stream()
                .map(book -> book.bookId() + " " + book.title() + " by " + book.author())
                .toList();
    }

    /** A book as Project Gutenberg publishes it: header, start marker, body, end marker and footer, with CRLF. */
    private static String gutenbergText(String title, String author, String language, String body) {
        String marker = " OF THE PROJECT GUTENBERG EBOOK " + title.toUpperCase(Locale.ROOT) + " ***";
        return String.join("\r\n", "The Project Gutenberg eBook of " + title, "", "Title: " + title, "",
                "Author: " + author, "", "Language: " + language, "", "*** START" + marker, "", body, "",
                "*** END" + marker, "", "Updated editions will replace the previous one.", "");
    }
}
