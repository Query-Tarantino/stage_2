package org.ulpgc.tarantino.common.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatalakePathsTest {

    @TempDir
    Path root;

    @Test
    void placesEachBookInTheDirectoryOfItsThousand() {
        DatalakePaths paths = new DatalakePaths(root);

        assertEquals(root.resolve("1/1342.header.txt"), paths.header(1342));
        assertEquals(root.resolve("1/1342.body.txt"), paths.body(1342));
        assertEquals(root.resolve("0/84.body.txt"), paths.body(84));
        assertEquals(root.resolve("0/999.body.txt"), paths.body(999));
        assertEquals(root.resolve("1/1000.body.txt"), paths.body(1000));
    }

    @Test
    void namesADifferentTemporaryNextToTheFileOnEveryWrite() {
        Path body = new DatalakePaths(root).body(1342);
        Path first = DatalakePaths.temporary(body);
        Path second = DatalakePaths.temporary(body);

        assertNotEquals(first, second);
        assertEquals(body.getParent(), first.getParent());
        assertTrue(first.getFileName().toString().matches("1342\\.body\\.txt\\.[0-9a-f-]{36}\\.tmp"), first::toString);
    }

    @Test
    void tellsTheTemporariesApartFromHeadersAndBodies() {
        DatalakePaths paths = new DatalakePaths(root);

        assertTrue(DatalakePaths.isTemporary(DatalakePaths.temporary(paths.body(1342))));
        assertTrue(DatalakePaths.isTemporary(DatalakePaths.temporary(paths.header(1342))));
        assertFalse(DatalakePaths.isTemporary(paths.header(1342)));
        assertFalse(DatalakePaths.isTemporary(paths.body(1342)));
    }

    @Test
    void writesAtomicallyOverAnyPreviousFileLeavingNoTemporary() throws IOException {
        Path body = new DatalakePaths(root).body(1342);

        DatalakePaths.writeAtomically(body, "first");
        DatalakePaths.writeAtomically(body, "second");

        assertEquals("second", Files.readString(body));
        try (Stream<Path> files = Files.list(body.getParent())) {
            assertEquals(List.of(body), files.toList());
        }
    }
}
