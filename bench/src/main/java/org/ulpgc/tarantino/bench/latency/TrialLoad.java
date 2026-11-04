package org.ulpgc.tarantino.bench.latency;

import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * What one trial of a {@code search-latency} run measured, from the successful requests of its
 * {@code load/<trial>.csv.gz} (SPEC §9): their latency, and how many were answered per second between the first and the
 * last of them.
 */
record TrialLoad(SummaryStats.Metrics latency, double requestsPerSecond) {

    private static final String SEPARATOR = ",";
    private static final String OK = "200";
    private static final int HEADER_LINES = 1;
    private static final int INSTANT = 0;
    private static final int STATUS = 2;
    private static final int LATENCY = 3;
    private static final double NANOS_PER_SECOND = 1e9;

    private record Request(Instant at, long latencyUs) {
    }

    static TrialLoad read(Path loadCsv) {
        try (BufferedReader rows = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(loadCsv)), StandardCharsets.UTF_8))) {
            return rows.lines()
                    .skip(HEADER_LINES)
                    .map(row -> row.split(SEPARATOR))
                    .filter(cells -> OK.equals(cells[STATUS]))
                    .map(cells -> new Request(Instant.parse(cells[INSTANT]), Long.parseLong(cells[LATENCY])))
                    .collect(Collectors.teeing(
                            Collectors.mapping(Request::latencyUs, Collectors.toList()),
                            Collectors.teeing(
                                    Collectors.mapping(Request::at, Collectors.minBy(Comparator.<Instant>naturalOrder())),
                                    Collectors.mapping(Request::at, Collectors.maxBy(Comparator.<Instant>naturalOrder())),
                                    TrialLoad::window),
                            TrialLoad::of));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Duration window(Optional<Instant> first, Optional<Instant> last) {
        return first.flatMap(start -> last.map(end -> Duration.between(start, end))).orElse(Duration.ZERO);
    }

    private static TrialLoad of(List<Long> latenciesUs, Duration window) {
        double seconds = window.toNanos() / NANOS_PER_SECOND;
        return new TrialLoad(SummaryStats.calculate(latenciesUs), seconds > 0 ? latenciesUs.size() / seconds : 0);
    }
}
