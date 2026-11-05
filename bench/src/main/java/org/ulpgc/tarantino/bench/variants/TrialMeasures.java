package org.ulpgc.tarantino.bench.variants;

import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.coordination.BookCoordination;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;
import org.ulpgc.tarantino.bench.visibility.BatchVisibility;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Derives the {@link TrialMetrics} of one trial from its transition and snapshot lines and the run's {@code stats.csv}. */
final class TrialMeasures {

    private static final String DOWNLOADING = "DOWNLOADING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String MONGO = "mongo";
    private static final String CONTROL = "control";
    private static final Map<String, String> COORDINATION_SERVICE = Map.of("hazelcast", "hazelcast", "activemq", "activemq",
            "activemq-persistent", "activemq", "polling", CONTROL, "orchestrator", CONTROL);
    private static final List<VariantMetric> BOOK_TIMES = List.of(VariantMetric.DOWNLOAD_QUEUE_WAIT, VariantMetric.DOWNLOAD,
            VariantMetric.INDEX_QUEUE_WAIT, VariantMetric.INDEXING, VariantMetric.TIME_TO_PUBLICATION);

    private TrialMeasures() {
    }

    static TrialMetrics of(String trial, String mode, int replicas, List<TransitionLine> lines, List<BatchVisibility> visibility,
                           List<ContainerStats> stats) {
        Map<VariantMetric, Double> values = new EnumMap<>(VariantMetric.class);
        List<TransitionLine> books = lines.stream().filter(TransitionLine::isBook).sorted(Comparator.comparing(TransitionLine::at)).toList();
        throughput(books).ifPresent(booksPerSecond -> values.put(VariantMetric.PIPELINE_THROUGHPUT, booksPerSecond));
        List<BookCoordination> coordination = BookCoordination.of(lines);
        if (!coordination.isEmpty()) {
            for (int column = 0; column < BOOK_TIMES.size(); column++) {
                values.put(BOOK_TIMES.get(column), p50(coordination, column));
            }
        }
        if (!visibility.isEmpty()) {
            values.put(VariantMetric.PUBLICATION_VISIBILITY,
                    SummaryStats.calculate(visibility.stream().map(batch -> batch.visibility().toMillis()).toList()).p50());
        }
        Map<String, Double> cpu = cpuByService(stats, books);
        putIfPresent(values, VariantMetric.COORDINATION_CPU, cpu.get(COORDINATION_SERVICE.get(mode)));
        putIfPresent(values, VariantMetric.MONGO_CPU, cpu.get(MONGO));
        return new TrialMetrics(trial, mode, replicas, values, cpu);
    }

    /** Books per second from the first {@code to=DOWNLOADING} to the last {@code to=PUBLISHED}, with {@link ThroughputWindow}. */
    private static Optional<Double> throughput(List<TransitionLine> books) {
        Optional<Instant> start = books.stream().filter(line -> DOWNLOADING.equals(line.to())).map(TransitionLine::at).findFirst();
        List<TransitionLine> published = books.stream().filter(line -> PUBLISHED.equals(line.to())).toList();
        if (start.isEmpty() || published.isEmpty()) {
            return Optional.empty();
        }
        int publishedBooks = (int) published.stream().map(TransitionLine::id).distinct().count();
        return Optional.of(ThroughputWindow.between(start.get(), published.getLast().at(), publishedBooks).booksPerSecond());
    }

    private static double p50(List<BookCoordination> books, int column) {
        return SummaryStats.calculate(books.stream().map(book -> book.times().get(column)).map(Duration::toMillis).toList()).p50();
    }

    /** The mean CPU of each container during the trial's transitions, summed over the containers of each service. */
    private static Map<String, Double> cpuByService(List<ContainerStats> stats, List<TransitionLine> books) {
        if (books.isEmpty()) {
            return Map.of();
        }
        Instant start = books.getFirst().at();
        Instant end = books.getLast().at();
        Map<String, Double> meanByContainer = stats.stream()
                .filter(sample -> !sample.at().isBefore(start) && !sample.at().isAfter(end))
                .collect(Collectors.groupingBy(ContainerStats::container, Collectors.averagingDouble(ContainerStats::cpuPercent)));
        return meanByContainer.entrySet().stream().collect(Collectors.groupingBy(container -> service(container.getKey()),
                Collectors.summingDouble(Map.Entry::getValue)));
    }

    /** {@code stage_2-crawler-1} is {@code crawler}: Compose names a container {@code <project>-<service>-<replica>}. */
    static String service(String container) {
        String[] parts = container.split("-");
        return parts.length < 3 ? container : parts[parts.length - 2];
    }

    private static void putIfPresent(Map<VariantMetric, Double> values, VariantMetric metric, Double value) {
        if (value != null) {
            values.put(metric, value);
        }
    }
}
