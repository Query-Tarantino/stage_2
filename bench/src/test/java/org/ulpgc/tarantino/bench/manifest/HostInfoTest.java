package org.ulpgc.tarantino.bench.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostInfoTest {

    @TempDir
    Path runsDirectory;

    @Test
    void describesThisHost() {
        HostInfo host = HostInfo.of(new ProcessCommandRunner(), runsDirectory);

        assertFalse(host.cpu().isBlank());
        assertEquals(Runtime.getRuntime().availableProcessors(), host.cores());
        assertTrue(host.memoryBytes() > 0);
        assertTrue(host.diskBytes() >= host.freeDiskBytes() && host.freeDiskBytes() > 0);
        assertTrue(host.operatingSystem().startsWith(System.getProperty("os.name")));
    }
}
