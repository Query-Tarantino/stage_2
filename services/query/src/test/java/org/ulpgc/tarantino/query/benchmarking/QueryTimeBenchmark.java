package org.ulpgc.tarantino.query.benchmarking;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.Heap;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.FootprintLog;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;
import org.ulpgc.tarantino.crawler.benchmarking.support.validation.Check;
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.benchmarking.support.PrebuiltIndexes;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.model.QueryTerms;
import org.ulpgc.tarantino.query.model.SearchResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class QueryTimeBenchmark {

    // The first opening of each process is discarded: for mongo it also creates the client
    private static final int INDEX_MEMORY_SAMPLES = 5;

    @Param({"json", "folders", "mongo"})
    public String index;

    @Param({"100", "300", "1000"})
    public int books;

    private BenchmarkStore store;
    private SearchCommand search;
    private Set<String> stopwords;
    private List<String> queries;
    private List<String> categories;
    private int[] categoryOfQuery;
    private long[] nanosPerCategory;
    private long[] queriesPerCategory;
    private boolean measured;

    @Setup(Level.Trial)
    public void openIndex() {
        IndexFixture fixture = IndexFixture.fromEnvironment();
        store = PrebuiltIndexes.of(index, books, fixture);
        stopwords = QueryWorkload.stopwords();
        List<WorkloadQuery> workload = QueryWorkload.queries();
        queries = QueryWorkload.texts(workload, QueryWorkload.ALL_CATEGORIES);
        categories = workload.stream().map(WorkloadQuery::category).distinct().toList();
        categoryOfQuery = workload.stream().mapToInt(query -> categories.indexOf(query.category())).toArray();
        search = openAndAnswerEveryQuery();
        for (int sample = 0; sample < INDEX_MEMORY_SAMPLES; sample++) {
            recordIndexMemory();
        }
        requireReferenceResults(fixture);
    }

    @Setup(Level.Iteration)
    public void resetCategoryTimes(IterationParams iteration) {
        nanosPerCategory = new long[categories.size()];
        queriesPerCategory = new long[categories.size()];
        measured = iteration.getType() == IterationType.MEASUREMENT;
    }

    /** A random query of any category, timed per category too, so one run measures every category (SPEC §11). */
    @Benchmark
    public SearchResult queryTime() {
        int query = ThreadLocalRandom.current().nextInt(queries.size());
        long start = System.nanoTime();
        SearchResult result = search.execute(queries.get(query));
        nanosPerCategory[categoryOfQuery[query]] += System.nanoTime() - start;
        queriesPerCategory[categoryOfQuery[query]]++;
        return result;
    }

    @TearDown(Level.Iteration)
    public void recordCategoryTimes() {
        if (!measured) {
            return;
        }
        for (int category = 0; category < categories.size(); category++) {
            if (queriesPerCategory[category] == 0) {
                continue;
            }
            double microsPerQuery = nanosPerCategory[category] / 1000.0 / queriesPerCategory[category];
            ResultRow sample = ResultRow.sample(index, "query_time_" + categories.get(category), books, microsPerQuery, "µs/query");
            FootprintLog.appendSample(BenchmarkRunner.SERVICE, sample);
        }
    }

    private void recordIndexMemory() {
        search = null;
        long before = Heap.usedAfterFullCollection();
        search = openAndAnswerEveryQuery();
        long indexMemory = Heap.usedAfterFullCollection() - before;
        FootprintLog.appendSample(BenchmarkRunner.SERVICE, ResultRow.sample(index, "index_memory", books, indexMemory, "bytes"));
    }

    private SearchCommand openAndAnswerEveryQuery() {
        SearchCommand opened = QueryWorkload.openSearch(store, index, stopwords);
        queries.forEach(opened::execute);
        return opened;
    }

    private void requireReferenceResults(IndexFixture fixture) {
        Map<String, List<Integer>> expected = referenceResults(fixture);
        queries.forEach(query -> Check.require(resultIds(query).equals(expected.get(query)), "wrong result for query '" + query + "'"));
    }

    private Map<String, List<Integer>> referenceResults(IndexFixture fixture) {
        Path cached = PrebuiltIndexes.file("reference-results-" + books + ".tsv");
        if (Files.exists(cached)) {
            return ReferenceResults.read(cached);
        }
        Map<String, List<Integer>> expected = computedReferenceResults(fixture);
        ReferenceResults.write(cached, expected);
        return expected;
    }

    private Map<String, List<Integer>> computedReferenceResults(IndexFixture fixture) {
        Map<String, Set<String>> termsOfQuery = new HashMap<>();
        Map<String, List<Integer>> expected = new HashMap<>();
        queries.forEach(query -> {
            termsOfQuery.put(query, QueryTerms.of(query, stopwords));
            expected.put(query, new ArrayList<>());
        });
        for (int bookId : fixture.dataset().ids(books).stream().sorted().toList()) {
            Set<String> bookTerms = fixture.terms(bookId);
            termsOfQuery.forEach((query, terms) -> {
                if (!terms.isEmpty() && bookTerms.containsAll(terms)) {
                    expected.get(query).add(bookId);
                }
            });
        }
        return expected;
    }

    private List<Integer> resultIds(String query) {
        return search.execute(query).books().stream().map(BookMetadata::bookId).toList();
    }
}
