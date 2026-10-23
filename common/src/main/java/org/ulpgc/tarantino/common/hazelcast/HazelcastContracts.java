package org.ulpgc.tarantino.common.hazelcast;

/** The queues, the topic and the cluster of SPEC §4.2; their messages are {@code TarantinoJson} text. */
public final class HazelcastContracts {

    public static final String BOOKS_TO_DOWNLOAD = "books-to-download";
    public static final String BOOKS_TO_INDEX = "books-to-index";
    public static final String PUBLISHED_BATCHES = "published-batches";
    public static final String DEFAULT_CLUSTER = "tarantino";

    private HazelcastContracts() {
    }
}
