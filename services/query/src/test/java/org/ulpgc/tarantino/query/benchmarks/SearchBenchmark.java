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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgs = {"-Xmx1g"})
public class SearchBenchmark {

    private static final Comparator<ScoredBook> RANKING = Comparator
            .comparingDouble(ScoredBook::score).reversed()
            .thenComparingInt(ScoredBook::bookId);

    private SearchCommand searchCommand;
    private Snapshot snapshot;
    private SearchQuery queryWithoutFilters;
    private SearchQuery queryWithFilters;
    private List<String> rankingTerms;
    private Map<String, Map<Integer, Integer>> rankingIndex;
    private List<Integer> candidateBookIds;
    private int totalBooks;

    @Setup
    public void setup() {
        totalBooks = 1000;
        Set<Integer> allBooks = new HashSet<>(IntStream.rangeClosed(1, totalBooks).boxed().toList());
        Set<String> batchIds = Set.of("batch_1", "batch_2");
        snapshot = new Snapshot(1, batchIds, allBooks);

        Map<String, TermPostings> postingsData = new HashMap<>();
        postingsData.put("love", createPostings("love", 1, 400));
        postingsData.put("night", createPostings("night", 200, 600));

        PostingsReader postingsReader = (gen, terms, batches) -> terms.stream()
                .filter(postingsData::containsKey)
                .map(postingsData::get)
                .toList();

        Set<Integer> austenBooks = new HashSet<>(IntStream.rangeClosed(1, 100).boxed().toList());
        MetadataReader metadataReader = new MetadataReader() {
            @Override
            public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
                return bookIds.stream()
                        .map(id -> new SearchResult(id, "Title " + id, "Austen", "en", 1998))
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

        rankingTerms = List.of("love", "night");
        rankingIndex = Map.of(
                "love", createTfMap(1, 400),
                "night", createTfMap(200, 600)
        );
        candidateBookIds = IntStream.rangeClosed(200, 400).boxed().toList();
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
    public List<ScoredBook> tfidfRanking() {
        return candidateBookIds.stream()
                .map(id -> new ScoredBook(id, score(id, rankingTerms, rankingIndex, totalBooks)))
                .sorted(RANKING)
                .toList();
    }

    private static double score(int bookId, List<String> terms, Map<String, Map<Integer, Integer>> index, int total) {
        return terms.stream()
                .map(index::get)
                .mapToDouble(books -> (1 + Math.log(books.get(bookId))) * Math.log((double) total / books.size()))
                .sum();
    }

    private static TermPostings createPostings(String term, int start, int end) {
        List<Integer> books = new ArrayList<>();
        List<Integer> tfs = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            books.add(i);
            tfs.add((i % 10) + 1);
        }
        return new TermPostings(term, books, tfs);
    }

    private static Map<Integer, Integer> createTfMap(int start, int end) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = start; i <= end; i++) {
            map.put(i, (i % 10) + 1);
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        String runDir = args.length > 0 ? args[0] : System.getProperty("jmh.run.dir", "benchmarks/runs/latest");
        File jmhDir = Paths.get(runDir, "jmh").toFile();
        jmhDir.mkdirs();
        File jsonResult = new File(jmhDir, "query.json");

        Options options = new OptionsBuilder()
                .include(SearchBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(10)
                .forks(1)
                .jvmArgs("-Xmx1g")
                .resultFormat(ResultFormatType.JSON)
                .result(jsonResult.getAbsolutePath())
                .build();

        new Runner(options).run();
    }

    public record ScoredBook(int bookId, double score) {
    }
}
