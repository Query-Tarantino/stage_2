package org.ulpgc.tarantino.bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchTest {

    @TempDir
    Path runDirectory;

    @Test
    void answersWrongUsageWithoutAKnownCommand() {
        assertEquals(Bench.WRONG_USAGE, Bench.run(List.of()));
        assertEquals(Bench.WRONG_USAGE, Bench.run(List.of("recover")));
    }

    @Test
    void passesTheRestOfTheArgumentsToTheCommand() throws IOException {
        Files.createDirectories(runDirectory.resolve("logs"));

        assertEquals(0, Bench.run(List.of("coordination", runDirectory.toString())));
        assertTrue(Files.exists(runDirectory.resolve("coordination.csv")));
        assertEquals(Bench.WRONG_USAGE, Bench.run(List.of("recovery-compare", "4")));
    }
}
