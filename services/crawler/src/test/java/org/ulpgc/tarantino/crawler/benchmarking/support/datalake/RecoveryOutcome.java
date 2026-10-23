package org.ulpgc.tarantino.crawler.benchmarking.support.datalake;

public record RecoveryOutcome(boolean recovered, long leftoverFiles) {
}
