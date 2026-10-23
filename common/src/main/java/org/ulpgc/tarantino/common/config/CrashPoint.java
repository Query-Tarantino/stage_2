package org.ulpgc.tarantino.common.config;

import java.util.Arrays;
import java.util.Optional;

/** The eight points of SPEC §8 where {@code TARANTINO_CRASH_AT} stops a service; two services share a name. */
public enum CrashPoint {
    CRAWLER_AFTER_DEQUEUE(TarantinoService.CRAWLER, "after-dequeue"),
    AFTER_CLAIM(TarantinoService.CRAWLER, "after-claim"),
    AFTER_DATALAKE_WRITE(TarantinoService.CRAWLER, "after-datalake-write"),
    AFTER_DOWNLOADED(TarantinoService.CRAWLER, "after-downloaded"),
    INDEXER_AFTER_DEQUEUE(TarantinoService.INDEXER, "after-dequeue"),
    DURING_BATCH_WRITE(TarantinoService.INDEXER, "during-batch-write"),
    AFTER_PUBLISH(TarantinoService.INDEXER, "after-publish"),
    AFTER_MARK(TarantinoService.INDEXER, "after-mark");

    private final TarantinoService service;
    private final String point;

    CrashPoint(TarantinoService service, String point) {
        this.service = service;
        this.point = point;
    }

    public TarantinoService service() {
        return service;
    }

    public String point() {
        return point;
    }

    static Optional<CrashPoint> of(TarantinoService service, String point) {
        return Arrays.stream(values())
                .filter(crashPoint -> crashPoint.service == service && crashPoint.point.equals(point))
                .findFirst();
    }
}
