package org.ulpgc.tarantino.crawler.model.book;

import java.util.List;

/** The body of {@code GET /ingest/list} (SPEC §6.5): the downloaded books in ascending order and how many there are. */
public record IngestList(int count, List<Integer> books) {

    public static IngestList of(List<Integer> books) {
        return new IngestList(books.size(), books);
    }
}
