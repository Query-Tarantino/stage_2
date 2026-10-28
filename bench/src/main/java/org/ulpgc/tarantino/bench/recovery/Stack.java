package org.ulpgc.tarantino.bench.recovery;

/** The endpoints the recovery checks ask, through nginx (SPEC §6). A request that gets no answer throws. */
public interface Stack {

    Answer controlStatus();

    Answer book(int bookId);

    Answer search(String query, int offset, int limit);
}
