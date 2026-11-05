package org.ulpgc.tarantino.bench.variants;

import java.util.Map;

/**
 * What one trial {@code mode-<mode>-replicas-<N>-rep-<n>} of {@code coordination-variants} measured: the value of each
 * {@link VariantMetric} it has and the mean CPU of each service, the sum of its containers.
 */
public record TrialMetrics(String trial, String mode, int replicas, Map<VariantMetric, Double> values,
                           Map<String, Double> cpuByService) {
}
