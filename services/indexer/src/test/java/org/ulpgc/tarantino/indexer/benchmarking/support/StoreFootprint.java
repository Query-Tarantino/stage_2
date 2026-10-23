package org.ulpgc.tarantino.indexer.benchmarking.support;

/** Disk taken by an inverted index, logical and in whole blocks, and its number of distinct terms. */
public record StoreFootprint(long bytes, long allocatedBytes, long terms) {
}
