package org.ulpgc.tarantino.indexer.adapters.metadata;

import java.nio.file.Path;

final class PortablePaths {

    private PortablePaths() {
    }

    static String of(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }
}
