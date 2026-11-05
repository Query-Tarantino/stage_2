package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.AxesChartStyler;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.bench.variants.ModePair;
import org.ulpgc.tarantino.bench.variants.TrialMetrics;
import org.ulpgc.tarantino.bench.variants.VariantMetric;
import org.ulpgc.tarantino.bench.variants.VariantsTool;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The seven charts of {@code coordination-variants} (SPEC §9): five metrics by replica count with one series per mode
 * and 95 % CI whiskers, publication visibility per mode, and the heatmap of the {@code pipeline_throughput} pairs at
 * the highest replica count, where a pair whose difference lies inside its CI is grey.
 */
public final class VariantsCharts {

    private static final String REPLICAS = "Replicas of crawler and indexer";
    private static final int HEATMAP_CELL = 130;
    private static final int HEATMAP_MARGIN = 170;
    private static final Color NOT_DIFFERENT = new Color(0xBDBDBD);
    private static final Color HIGHER = new Color(0x4C9F70);
    private static final Color LOWER = new Color(0xD1495B);
    private static final Map<VariantMetric, String> BY_REPLICAS = Map.of(
            VariantMetric.PIPELINE_THROUGHPUT, "pipeline-throughput",
            VariantMetric.TIME_TO_PUBLICATION, "time-to-publication",
            VariantMetric.DOWNLOAD_QUEUE_WAIT, "download-queue-wait",
            VariantMetric.COORDINATION_CPU, "coordination-cpu",
            VariantMetric.MONGO_CPU, "mongo-cpu");

    private VariantsCharts() {
    }

    public static void write(Path runDirectory, List<TrialMetrics> trials, List<ModePair> pairs) {
        if (trials.isEmpty()) {
            return;
        }
        BY_REPLICAS.forEach((metric, name) -> writeIfMeasured(byReplicas(trials, metric), runDirectory, name));
        writeIfMeasured(visibility(trials), runDirectory, "publication-visibility");
        int highest = trials.stream().mapToInt(TrialMetrics::replicas).max().orElseThrow();
        RunChart.write(heatmap(pairs, highest), runDirectory, "pipeline-throughput-pairs");
    }

    /** A metric no trial measured, such as CPU without {@code stats.csv}, has no chart. */
    private static void writeIfMeasured(Chart<? extends AxesChartStyler, ?> chart, Path runDirectory, String name) {
        if (!chart.getSeriesMap().isEmpty()) {
            RunChart.write(chart, runDirectory, name);
        }
    }

    private static XYChart byReplicas(List<TrialMetrics> trials, VariantMetric metric) {
        XYChart chart = new XYChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(metric.column() + " by replicas").xAxisTitle(REPLICAS).yAxisTitle(metric.unit()).build();
        chart.getStyler().setErrorBarsColorSeriesColor(true);
        List<Integer> replicas = List.copyOf(trials.stream().map(TrialMetrics::replicas).collect(Collectors.toCollection(TreeSet::new)));
        VariantsTool.MODES.forEach(mode -> series(trials, metric, replicas, mode)
                .ifPresent(series -> chart.addSeries(mode, series.x(), series.mean(), series.ci95())));
        return chart;
    }

    private static Optional<Series> series(List<TrialMetrics> trials, VariantMetric metric, List<Integer> replicas, String mode) {
        List<Integer> measured = replicas.stream().filter(count -> !VariantsTool.values(trials, metric, count, mode).isEmpty()).toList();
        if (measured.isEmpty()) {
            return Optional.empty();
        }
        Function<Integer, SummaryStats.Metrics> stats = count -> SummaryStats.calculate(VariantsTool.values(trials, metric, count, mode));
        return Optional.of(new Series(measured.stream().mapToDouble(Integer::doubleValue).toArray(),
                measured.stream().mapToDouble(count -> stats.apply(count).mean()).toArray(),
                measured.stream().mapToDouble(count -> stats.apply(count).ci95()).toArray()));
    }

    private static CategoryChart visibility(List<TrialMetrics> trials) {
        VariantMetric metric = VariantMetric.PUBLICATION_VISIBILITY;
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(metric.column() + " per mode").xAxisTitle("Mode").yAxisTitle(metric.unit()).build();
        trials.stream().map(TrialMetrics::replicas).collect(Collectors.toCollection(TreeSet::new)).forEach(replicas -> {
            List<String> modes = VariantsTool.MODES.stream()
                    .filter(mode -> !VariantsTool.values(trials, metric, replicas, mode).isEmpty()).toList();
            if (!modes.isEmpty()) {
                chart.addSeries("replicas " + replicas, modes, modes.stream()
                        .map(mode -> SummaryStats.calculate(VariantsTool.values(trials, metric, replicas, mode)).mean()).toList());
            }
        });
        return chart;
    }

    private static BufferedImage heatmap(List<ModePair> pairs, int replicas) {
        List<String> modes = VariantsTool.MODES;
        int size = HEATMAP_MARGIN + modes.size() * HEATMAP_CELL;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(Color.DARK_GRAY);
        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 14f));
        graphics.drawString("pipeline_throughput, row minus column, replicas " + replicas + " (books/s)", 10, 24);
        graphics.setFont(graphics.getFont().deriveFont(Font.PLAIN, 13f));
        for (int index = 0; index < modes.size(); index++) {
            graphics.drawString(modes.get(index), 10, HEATMAP_MARGIN + index * HEATMAP_CELL + HEATMAP_CELL / 2);
            graphics.drawString(modes.get(index), HEATMAP_MARGIN + index * HEATMAP_CELL + 8, HEATMAP_MARGIN - 12);
        }
        for (int row = 0; row < modes.size(); row++) {
            for (int column = 0; column < modes.size(); column++) {
                cell(graphics, pair(pairs, replicas, modes.get(row), modes.get(column)), row, column);
            }
        }
        graphics.dispose();
        return image;
    }

    private static void cell(Graphics2D graphics, Optional<Cell> cell, int row, int column) {
        int x = HEATMAP_MARGIN + column * HEATMAP_CELL;
        int y = HEATMAP_MARGIN + row * HEATMAP_CELL;
        graphics.setColor(cell.map(Cell::color).orElse(Color.WHITE));
        graphics.fillRect(x, y, HEATMAP_CELL - 2, HEATMAP_CELL - 2);
        graphics.setColor(Color.BLACK);
        cell.ifPresent(present -> graphics.drawString(String.format(Locale.ROOT, "%+.1f", present.difference()), x + 40, y + HEATMAP_CELL / 2));
    }

    private static Optional<Cell> pair(List<ModePair> pairs, int replicas, String rowMode, String columnMode) {
        return pairs.stream()
                .filter(pair -> pair.metric() == VariantMetric.PIPELINE_THROUGHPUT && pair.replicas() == replicas)
                .filter(pair -> pair.modeA().equals(rowMode) && pair.modeB().equals(columnMode)
                        || pair.modeA().equals(columnMode) && pair.modeB().equals(rowMode))
                .findFirst()
                .map(pair -> new Cell(pair.modeA().equals(rowMode) ? pair.difference() : -pair.difference(), pair.differs()));
    }

    private record Series(double[] x, double[] mean, double[] ci95) {
    }

    private record Cell(double difference, boolean differs) {

        Color color() {
            if (!differs) {
                return NOT_DIFFERENT;
            }
            return difference > 0 ? HIGHER : LOWER;
        }
    }
}
