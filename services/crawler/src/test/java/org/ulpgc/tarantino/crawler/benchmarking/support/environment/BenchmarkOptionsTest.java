package org.ulpgc.tarantino.crawler.benchmarking.support.environment;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.options.Options;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BenchmarkOptionsTest {

    @Test
    void runsASecondPassWithEveryParameterInReverseOrder() {
        List<Options> passes = BenchmarkOptions.passes("crawler", Map.<String, String>of()::get);

        assertEquals(2, passes.size());
        assertFalse(passes.getFirst().getParameter("layout").hasValue());
        assertEquals(List.of("batch", "book", "time"), List.copyOf(passes.get(1).getParameter("layout").get()));
        assertEquals(List.of("1000", "300", "100"), List.copyOf(passes.get(1).getParameter("books").get()));
    }

    @Test
    void reversesOverriddenSizesWithoutRepeatingThem() {
        List<Options> passes = BenchmarkOptions.passes("crawler", Map.of("TARANTINO_BENCHMARK_BOOKS", "100,300")::get);

        assertEquals(List.of("100", "300"), List.copyOf(passes.getFirst().getParameter("books").get()));
        assertEquals(List.of("300", "100"), List.copyOf(passes.get(1).getParameter("books").get()));
    }

    @Test
    void runsOnePassInQuickMode() {
        assertEquals(1, BenchmarkOptions.passes("crawler", Map.of("TARANTINO_BENCHMARK_QUICK", "true")::get).size());
    }
}
