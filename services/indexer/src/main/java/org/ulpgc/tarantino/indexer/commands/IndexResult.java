package org.ulpgc.tarantino.indexer.commands;

public record IndexResult(int bookId, boolean indexed, int uniqueTerms) {

    public static IndexResult success(int bookId, int uniqueTerms) {
        return new IndexResult(bookId, true, uniqueTerms);
    }

    public static IndexResult notFound(int bookId) {
        return new IndexResult(bookId, false, 0);
    }
}
