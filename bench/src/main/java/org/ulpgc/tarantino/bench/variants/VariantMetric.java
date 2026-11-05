package org.ulpgc.tarantino.bench.variants;

/** The metrics every pair of coordination modes is compared on in {@code pairs.csv}, one value per trial (SPEC §9). */
public enum VariantMetric {
    PIPELINE_THROUGHPUT("pipeline_throughput", "books/s"),
    TIME_TO_PUBLICATION("time_to_publication_p50", "ms"),
    DOWNLOAD_QUEUE_WAIT("download_queue_wait_p50", "ms"),
    INDEX_QUEUE_WAIT("index_queue_wait_p50", "ms"),
    DOWNLOAD("download_ms_p50", "ms"),
    INDEXING("indexing_ms_p50", "ms"),
    COORDINATION_CPU("coordination_cpu", "%"),
    MONGO_CPU("mongo_cpu", "%"),
    PUBLICATION_VISIBILITY("publication_visibility_p50", "ms");

    private final String column;
    private final String unit;

    VariantMetric(String column, String unit) {
        this.column = column;
        this.unit = unit;
    }

    public String column() {
        return column;
    }

    public String unit() {
        return unit;
    }
}
