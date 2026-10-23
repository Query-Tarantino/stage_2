package org.ulpgc.tarantino.indexer.adapters.stopwords;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileStopwordsLoaderTest {

    @TempDir
    Path directory;

    @Test
    void stripsAndLowercasesEntriesIgnoringBlankLines() throws IOException {
        Path file = Files.writeString(directory.resolve("stopwords.txt"), " The \n\nOF\r\nand\n");

        assertEquals(Set.of("the", "of", "and"), new FileStopwordsLoader(file).stopwords());
    }
}
