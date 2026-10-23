package org.ulpgc.tarantino.crawler.benchmarking.support.environment;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.format.OutputFormat;
import org.openjdk.jmh.runner.format.OutputFormatFactory;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

public final class BenchmarkOptions {

    private static final TimeValue QUICK_ITERATION = TimeValue.milliseconds(200);
    private static final OutputFormat SILENT = OutputFormatFactory.createFormatInstance(System.out, VerboseMode.SILENT);

    private BenchmarkOptions() {
    }

    /**
     * The service's benchmarks in passes of one process per configuration (SPEC §11): two in a full run, the
     * second with the values of every parameter in reverse order, so that whatever drifts during a run, such as
     * the temperature or the writes left by the benchmark before, weighs alike on every structure and size
     * instead of always on the last ones; one pass in quick mode.
     */
    public static List<Options> passes(String service) {
        return passes(service, System::getenv);
    }

    static List<Options> passes(String service, UnaryOperator<String> environment) {
        String benchmarks = "org\\.ulpgc\\.tarantino\\." + service + "\\.benchmarking\\..*";
        ChainedOptionsBuilder firstPass = pass(service, benchmarks, 1);
        applyOverrides(firstPass, environment);
        Options first = firstPass.build();
        if (variable(environment, "TARANTINO_BENCHMARK_QUICK").filter(Boolean::parseBoolean).isPresent()) {
            return List.of(first);
        }
        // Every parameter is set here, overridden ones included, as param() adds to the values already set
        ChainedOptionsBuilder second = pass(service, benchmarks, 2);
        reversedParameters(benchmarks, first).forEach(second::param);
        return List.of(first, second.build());
    }

    private static ChainedOptionsBuilder pass(String service, String benchmarks, int pass) {
        return new OptionsBuilder()
                .include(benchmarks)
                .forks(1)
                .resultFormat(ResultFormatType.CSV)
                .result(BenchmarkPaths.benchmarks().resolve(service).resolve("jmh-results-pass-" + pass + ".csv").toString())
                .shouldFailOnError(true);
    }

    private static void applyOverrides(ChainedOptionsBuilder options, UnaryOperator<String> environment) {
        variable(environment, "TARANTINO_BENCHMARK_BOOKS").ifPresent(books -> options.param("books", books.split(",")));
        variable(environment, "TARANTINO_BENCHMARK_QUICK").filter(Boolean::parseBoolean).ifPresent(quick -> quick(options));
        variable(environment, "TARANTINO_BENCHMARK_SKIP_MONGO").filter(Boolean::parseBoolean).ifPresent(skip -> withoutMongo(options));
    }

    /** The values of every parameter of the benchmarks, as the first pass runs them, in reverse order. */
    private static Map<String, String[]> reversedParameters(String benchmarks, Options first) {
        Map<String, String[]> reversed = new TreeMap<>();
        BenchmarkList.defaultList().find(SILENT, List.of(benchmarks), List.of()).forEach(benchmark ->
                benchmark.getParams().orElse(Map.of()).forEach((name, declared) -> {
                    List<String> values = new ArrayList<>(first.getParameter(name).orElse(List.of(declared)));
                    reversed.merge(name, values.reversed().toArray(String[]::new), BenchmarkOptions::sameValues);
                }));
        return reversed;
    }

    private static String[] sameValues(String[] values, String[] otherValues) {
        if (!Arrays.equals(values, otherValues)) {
            throw new IllegalStateException("Benchmarks declare different values for the same parameter: "
                    + Arrays.toString(values) + " and " + Arrays.toString(otherValues));
        }
        return values;
    }

    private static void quick(ChainedOptionsBuilder options) {
        options.forks(1).warmupIterations(1).measurementIterations(1).warmupTime(QUICK_ITERATION).measurementTime(QUICK_ITERATION);
    }

    private static void withoutMongo(ChainedOptionsBuilder options) {
        options.param("index", "json", "folders").param("metadata", "sqlite");
    }

    private static Optional<String> variable(UnaryOperator<String> environment, String name) {
        return Optional.ofNullable(environment.apply(name)).filter(value -> !value.isBlank());
    }
}
