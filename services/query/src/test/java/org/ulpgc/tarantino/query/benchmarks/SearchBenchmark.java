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

import java.io.File;
import java.nio.file.Paths;
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
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 3, jvmArgs = {"-Xmx1g"})
public class SearchBenchmark {

    private SearchCommand searchCommand;
    private Snapshot snapshot;
    private SearchQuery queryWithoutFilters;
    private SearchQuery queryWithFilters;
    private SearchQuery rankingQuery;

    @Setup
    public void setup() {
        int totalBooks = 1000;
        Set<Integer> allBooks = IntStream.rangeClosed(1, totalBooks).boxed().collect(Collectors.toSet());
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

        Set<Integer> austenBooks = IntStream.rangeClosed(1, 100).boxed().collect(Collectors.toSet());
        MetadataReader metadataReader = new MetadataReader() {
            @Override
            public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
                return bookIds.stream()
                        .map(bookId -> new SearchResult(bookId, "Title " + bookId, "Austen", "en", 1998))
                        .toList();
            }

            @Override
            public Set<Integer> matchingBookIds(int generation, Collection<String> authorTerms, String language, Integer year) {
                return austenBooks;
            }
        };

        searchCommand = new SearchCommand(postingsReader, metadataReader, new Tokenizer(Set.of()));
        queryWithoutFilters = new SearchQuery("love night", 0, 20);
        queryWithFilters = new SearchQuery("love night", "Austen", List.of("austen"), "en", 1998, 0, 20);
        rankingQuery = new SearchQuery("love night time", 0, 100);
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
                .map(bookId -> (bookId % 10) + 1)
                .boxed()
                .toList();
        return new TermPostings(term, books, termFrequencies);
    }

    public static void main(String[] args) throws Exception {
        String runDir = args.length > 0 ? args[0] : System.getProperty("jmh.run.dir", "benchmarks/runs/latest");
        File jmhDir = Paths.get(runDir, "jmh").toFile();
        jmhDir.mkdirs();
        File jsonResult = new File(jmhDir, "query.json");

        Options options = new OptionsBuilder()
                .include(SearchBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result(jsonResult.getAbsolutePath())
                .build();

        new Runner(options).run();
    }
}
