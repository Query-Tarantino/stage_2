package org.ulpgc.tarantino.control.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.control.ports.DatalakeTemporaries;

import java.nio.file.Path;
import java.util.List;

/** The datalake cleanup of SPEC §4.6, every {@code TARANTINO_CLEANUP_SECONDS}: deletes the orphan temporaries only. */
public class CleanDatalakeCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanDatalakeCommand.class);

    private final DatalakeTemporaries temporaries;

    public CleanDatalakeCommand(DatalakeTemporaries temporaries) {
        this.temporaries = temporaries;
    }

    /** If the datalake cannot be read, it logs it without throwing, so the scheduled loop keeps running. */
    public void execute() {
        try {
            List<Path> orphans = temporaries.orphans();
            orphans.forEach(temporaries::delete);
            LOGGER.info("Deleted {} orphan temporaries from the datalake", orphans.size());
        } catch (RuntimeException e) {
            LOGGER.warn("Datalake cleanup stopped, the next one starts over: {}", e.getMessage());
        }
    }
}
