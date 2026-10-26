package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.control.model.DownloadLease;

import java.util.List;
import java.util.Optional;

public interface DownloadLeases {

    /** The {@code DOWNLOADING} books whose lease has passed by the server's time (SPEC §5.3, step 1). */
    List<DownloadLease> expired();

    /** “Release an expired download” (SPEC §5.1): the state it left, or empty if the worker renewed or finished first. */
    Optional<BookStatus> release(DownloadLease lease);
}
