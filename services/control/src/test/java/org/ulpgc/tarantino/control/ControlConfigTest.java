package org.ulpgc.tarantino.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlConfigTest {

    @Test
    void readsPositiveIntegers() {
        assertEquals(100, ControlConfig.positiveInteger("TARANTINO_INDEX_BATCH", "100"));
        assertEquals(1, ControlConfig.positiveInteger("TARANTINO_PARALLEL_DOWNLOADS", " 1 "));
    }

    @Test
    void rejectsValuesThatAreNotPositiveIntegersNamingTheVariable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ControlConfig.positiveInteger("TARANTINO_PARALLEL_DOWNLOADS", "0"));

        assertEquals("Unknown TARANTINO_PARALLEL_DOWNLOADS: 0 (expected a positive integer)", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ControlConfig.positiveInteger("TARANTINO_INDEX_BATCH", "many"));
    }
}
