package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.control.adapters.LocalMirror;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ControlFactory {

    private static final String DEFAULT_CANDIDATES = "sample_ids.txt";

    private ControlFactory() {
    }

    /**
     * The ids of the workload file given, in order. Without one, every book of the local mirror in ascending id
     * order when there is a mirror, or else the sample dataset.
     */
    public static List<Integer> candidates(ControlConfig config, Optional<String> file) {
        if (file.isEmpty() && config.mirror() != null) {
            return new LocalMirror(config.mirror()).bookIds();
        }
        return ids(config.workload().resolve(file.orElse(DEFAULT_CANDIDATES)));
    }

    private static List<Integer> ids(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
