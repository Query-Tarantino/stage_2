package org.ulpgc.tarantino.bench.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCommandRunnerTest {

    private static final String JAVA = ProcessHandle.current().info().command().orElseThrow();

    @Test
    void answersWhatTheCommandPrinted() {
        String output = new ProcessCommandRunner().output(List.of(JAVA, "--version"));

        assertTrue(output.contains(String.valueOf(Runtime.version().feature())), output);
    }

    @Test
    void failsWhenTheCommandExitsWithAnError() {
        assertThrows(IllegalStateException.class, () -> new ProcessCommandRunner().output(List.of(JAVA, "--no-such-option")));
    }
}
