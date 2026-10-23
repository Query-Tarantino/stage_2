package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.control.model.Outcome;

import java.util.concurrent.CompletableFuture;

public interface Crawler {

    /**
     * Starts ingesting a book (SPEC §9): a book the datalake already holds is not downloaded again, and any other is
     * downloaded in the background, several at once, without writing anything. Once the download finishes, the caller
     * stores it with {@link Download#store()}, so only the caller's thread uses the datalake.
     */
    CompletableFuture<Download> ingest(int bookId);

    /** A finished download, or a book already in the datalake, waiting to be stored. */
    @FunctionalInterface
    interface Download {

        Outcome store();
    }
}
