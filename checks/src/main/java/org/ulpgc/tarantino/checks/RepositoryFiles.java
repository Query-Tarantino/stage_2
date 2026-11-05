package org.ulpgc.tarantino.checks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Finds a file of the repository, such as {@code api/openapi.yaml}, from the root or from a module folder (SPEC §2.3). */
final class RepositoryFiles {

    private static final List<String> FROM_ROOT = List.of("", "../", "../../");

    private RepositoryFiles() {
    }

    static Optional<Path> find(String relativePath) {
        return FROM_ROOT.stream().map(prefix -> Path.of(prefix + relativePath)).filter(Files::exists).findFirst();
    }

    static Path require(String relativePath) {
        return find(relativePath).orElseThrow(() -> new IllegalStateException("Could not find file: " + relativePath));
    }
}
