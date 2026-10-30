package org.ulpgc.tarantino.bench.manifest;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** The host a run measured on (SPEC §9): CPU, cores, RAM, the disk that holds the runs and the operating system. */
public record HostInfo(String cpu, int cores, long memoryBytes, long diskBytes, long freeDiskBytes, String operatingSystem) {

    static final List<String> WINDOWS_CPU = List.of("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name");
    static final List<String> MAC_CPU = List.of("sysctl", "-n", "machdep.cpu.brand_string");

    private static final Path LINUX_CPU_INFO = Path.of("/proc/cpuinfo");
    private static final String LINUX_CPU_MODEL = "model name";
    private static final String OS_NAME = System.getProperty("os.name");

    public static HostInfo of(CommandRunner runner, Path runsDirectory) {
        FileStore disk = fileStore(runsDirectory);
        OperatingSystemMXBean system = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return new HostInfo(cpu(runner), Runtime.getRuntime().availableProcessors(), system.getTotalMemorySize(),
                space(disk, FileStore::getTotalSpace), space(disk, FileStore::getUsableSpace),
                OS_NAME + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
    }

    private static String cpu(CommandRunner runner) {
        String osName = OS_NAME.toLowerCase(Locale.ROOT);
        if (osName.startsWith("windows")) {
            return runner.output(WINDOWS_CPU).strip();
        }
        if (osName.startsWith("mac")) {
            return runner.output(MAC_CPU).strip();
        }
        return linuxCpu();
    }

    private static String linuxCpu() {
        try {
            return Files.readAllLines(LINUX_CPU_INFO).stream()
                    .filter(line -> line.startsWith(LINUX_CPU_MODEL))
                    .map(line -> line.substring(line.indexOf(':') + 1).strip())
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FileStore fileStore(Path directory) {
        try {
            return Files.getFileStore(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long space(FileStore disk, SpaceReading reading) {
        try {
            return reading.bytes(disk);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface SpaceReading {

        long bytes(FileStore disk) throws IOException;
    }
}
