package org.ulpgc.tarantino.control.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileControlStateStoreTest {

    @TempDir
    Path root;

    @Test
    void startsEmptyWhenNoControlFilesExist() {
        FileControlStateStore state = new FileControlStateStore(root.resolve("control"));

        assertEquals(Set.of(), state.downloaded());
        assertEquals(Set.of(), state.indexed());
    }

    @Test
    void appendsIdsWithLfKeepingFileOrder() throws IOException {
        FileControlStateStore state = new FileControlStateStore(root.resolve("control"));
        state.markDownloaded(1342);
        state.markDownloaded(84);
        state.markIndexed(84);

        assertEquals(List.of(1342, 84), List.copyOf(state.downloaded()));
        assertEquals(Set.of(84), state.indexed());
        assertEquals("1342\n84\n", Files.readString(root.resolve("control/downloaded_books.txt")));
    }

    @Test
    void ignoresBlankAndPartiallyWrittenLines() throws IOException {
        Files.writeString(root.resolve("downloaded_books.txt"), "5\n\n 84 \n13x");

        assertEquals(List.of(5, 84), List.copyOf(new FileControlStateStore(root).downloaded()));
    }
}
