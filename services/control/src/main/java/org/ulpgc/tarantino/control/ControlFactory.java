package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.control.adapters.LocalMirror;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ControlFactory {

    private ControlFactory() {
    }

    /** The books to seed (SPEC §4.7): every book of the local mirror, or the ids of a workload file in its order. */
    public static List<Integer> candidates(TarantinoConfig config) {
        if (TarantinoConfig.MIRROR_CANDIDATES.equals(config.candidates())) {
            return new LocalMirror(config.mirror().orElseThrow()).bookIds();
        }
        return ids(config.workload().resolve(config.candidates()));
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
