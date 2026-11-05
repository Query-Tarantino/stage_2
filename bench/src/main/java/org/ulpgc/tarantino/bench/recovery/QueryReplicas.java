package org.ulpgc.tarantino.bench.recovery;

import java.util.List;

/** Every {@code query} replica, asked directly instead of through nginx, which would answer from only one of them. */
@FunctionalInterface
public interface QueryReplicas {

    /** The {@code total_books} of each replica's {@code GET /search/filters} (SPEC §6.4); a replica that does not answer counts 0. */
    List<Integer> snapshotBooks();
}
