package org.ulpgc.tarantino.indexer.adapters.metadata;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortablePathsTest {

    @Test
    void useForwardSlashes() {
        assertEquals("datalake/20250925/14/5.body.txt", PortablePaths.of(Path.of("datalake", "20250925", "14", "5.body.txt")));
    }
}
