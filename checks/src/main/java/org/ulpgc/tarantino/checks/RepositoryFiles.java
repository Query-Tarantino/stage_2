package org.ulpgc.tarantino.checks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A file of the repository, found from the repository root or from a module folder, wherever the checks run. */
final class RepositoryFiles {

    private static final List<String> FOLDERS_UP = List.of("", "../", "../../");

    private RepositoryFiles() {
    }

    static Optional<Path> find(String relativePath) {
        return FOLDERS_UP.stream()
                .map(folder -> Path.of(folder + relativePath))
                .filter(Files::exists)
                .findFirst();
    }
}
