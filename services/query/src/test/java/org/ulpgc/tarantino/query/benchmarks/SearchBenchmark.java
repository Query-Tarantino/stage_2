package org.ulpgc.tarantino.query.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.SearchResponse;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.MetadataReader;
import org.ulpgc.tarantino.query.ports.PostingsReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = BenchmarkProtocol.WARMUP_ITERATIONS, time = SearchBenchmark.ITERATION_MILLISECONDS, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = BenchmarkProtocol.MEASUREMENT_ITERATIONS, time = SearchBenchmark.ITERATION_MILLISECONDS, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = BenchmarkProtocol.FORKS, jvmArgs = BenchmarkProtocol.FIXED_HEAP)
public class SearchBenchmark {

    static final int ITERATION_MILLISECONDS = 200;

    private static final int BOOKS = 1_000;
    private static final int AUSTEN_BOOKS = 100;
    private static final int YEAR = 1998;
    private static final int PAGE_SIZE = 20;
    private static final int RANKING_PAGE_SIZE = 100;
    private static final int TERM_FREQUENCY_CYCLE = 10;

    private SearchCommand searchCommand;
    private Snapshot snapshot;
    private SearchQuery queryWithoutFilters;
    private SearchQuery queryWithFilters;
    private SearchQuery rankingQuery;

    @Setup
    public void setup() {
        Set<Integer> allBooks = IntStream.rangeClosed(1, BOOKS).boxed().collect(Collectors.toSet());
        Set<String> batchIds = Set.of("batch_1", "batch_2");
        snapshot = new Snapshot(1, batchIds, allBooks);

        Map<String, TermPostings> postingsByTerm = Map.of(
                "love", createPostings("love", 1, 400),
                "night", createPostings("night", 200, 600),
                "time", createPostings("time", 100, 500)
        );

        PostingsReader postingsReader = (generation, terms, batches) -> terms.stream()
                .filter(postingsByTerm::containsKey)
                .map(postingsByTerm::get)
                .toList();

        Set<Integer> austenBooks = IntStream.rangeClosed(1, AUSTEN_BOOKS).boxed().collect(Collectors.toSet());
        MetadataReader metadataReader = new MetadataReader() {
            @Override
            public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
                return bookIds.stream()
                        .map(bookId -> new SearchResult(bookId, "Title " + bookId, "Austen", "en", YEAR))
                        .toList();
            }

            @Override
            public Set<Integer> matchingBookIds(int generation, Collection<String> authorTerms, String language, Integer year) {
                return austenBooks;
            }
        };

        searchCommand = new SearchCommand(postingsReader, metadataReader, new Tokenizer(Set.of()));
        queryWithoutFilters = new SearchQuery("love night", 0, PAGE_SIZE);
        queryWithFilters = new SearchQuery("love night", "Austen", List.of("austen"), "en", YEAR, 0, PAGE_SIZE);
        rankingQuery = new SearchQuery("love night time", 0, RANKING_PAGE_SIZE);
    }

    @Benchmark
    public SearchResponse searchWithoutFilters() {
        return searchCommand.execute(snapshot, queryWithoutFilters);
    }

    @Benchmark
    public SearchResponse searchWithFilters() {
        return searchCommand.execute(snapshot, queryWithFilters);
    }

    @Benchmark
    public SearchResponse tfidfRanking() {
        return searchCommand.execute(snapshot, rankingQuery);
    }

    private static TermPostings createPostings(String term, int start, int end) {
        List<Integer> books = IntStream.rangeClosed(start, end).boxed().toList();
        List<Integer> termFrequencies = IntStream.rangeClosed(start, end)
                .map(bookId -> (bookId % TERM_FREQUENCY_CYCLE) + 1)
                .boxed()
                .toList();
        return new TermPostings(term, books, termFrequencies);
    }

    /** {@code args[0]} is the run directory, {@code benchmarks/runs/<run_id>}; the JSON goes to its {@code jmh/query.json}. */
    public static void main(String[] args) throws IOException, RunnerException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SearchBenchmark <run directory>");
        }
        Path jmh = Files.createDirectories(Path.of(args[0], "jmh"));
        Options options = new OptionsBuilder()
                .include(SearchBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result(jmh.resolve("query.json").toString())
                .build();
        new Runner(options).run();
    }
}
