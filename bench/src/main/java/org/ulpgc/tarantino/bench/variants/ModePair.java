package org.ulpgc.tarantino.bench.variants;

import org.apache.commons.math3.distribution.TDistribution;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One row of {@code pairs.csv}: the difference between the means of two modes for one metric and replica count, with
 * the half-width of its 95 % CI by Student's t with {@code min(n_a, n_b) - 1} degrees of freedom, which is
 * conservative, and the standard error {@code sqrt(s_a²/n_a + s_b²/n_b)} (SPEC §9).
 */
public record ModePair(VariantMetric metric, int replicas, String modeA, String modeB, double meanA, double meanB,
                       Optional<Double> ci95HalfWidth) {

    public static final String HEADER = "metric,replicas,mode_a,mode_b,mean_a,mean_b,difference,ci95_half_width,differs,unit";

    private static final double UPPER_TAIL = 0.975;

    static ModePair of(VariantMetric metric, int replicas, String modeA, List<Double> valuesA, String modeB, List<Double> valuesB) {
        int degreesOfFreedom = Math.min(valuesA.size(), valuesB.size()) - 1;
        Optional<Double> halfWidth = degreesOfFreedom < 1
                ? Optional.empty()
                : Optional.of(new TDistribution(degreesOfFreedom).inverseCumulativeProbability(UPPER_TAIL)
                        * Math.sqrt(variance(valuesA) / valuesA.size() + variance(valuesB) / valuesB.size()));
        return new ModePair(metric, replicas, modeA, modeB, mean(valuesA), mean(valuesB), halfWidth);
    }

    public double difference() {
        return meanA - meanB;
    }

    /** Whether the difference lies outside its 95 % CI; never without a CI. */
    public boolean differs() {
        return ci95HalfWidth.map(halfWidth -> Math.abs(difference()) > halfWidth).orElse(false);
    }

    public String row() {
        return String.format(Locale.ROOT, "%s,%d,%s,%s,%.3f,%.3f,%.3f,%s,%s,%s", metric.column(), replicas, modeA, modeB, meanA, meanB,
                difference(), ci95HalfWidth.map(halfWidth -> String.format(Locale.ROOT, "%.3f", halfWidth)).orElse(""), differs(),
                metric.unit());
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static double variance(List<Double> values) {
        double mean = mean(values);
        return values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum() / (values.size() - 1);
    }
}
