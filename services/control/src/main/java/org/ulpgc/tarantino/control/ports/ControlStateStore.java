package org.ulpgc.tarantino.control.ports;

import java.util.Set;

public interface ControlStateStore {

    Set<Integer> downloaded();

    Set<Integer> indexed();

    void markDownloaded(int bookId);

    void markIndexed(int bookId);
}
