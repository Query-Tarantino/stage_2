package org.ulpgc.tarantino.control.ports;

import java.nio.file.Path;
import java.util.List;

public interface DatalakeTemporaries {

    /**
     * The datalake's temporaries whose {@code mtime} is older than the container clock minus
     * {@code TARANTINO_TMP_MAX_AGE_SECONDS}: orphans, since a temporary only exists while it is written (SPEC §4.6).
     */
    List<Path> orphans();

    /** Deletes the temporary, unless it is already gone. */
    void delete(Path temporary);
}
