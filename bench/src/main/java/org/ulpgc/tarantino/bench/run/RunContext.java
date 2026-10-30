package org.ulpgc.tarantino.bench.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RunContext {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String runId;
    private final Path runDirectory;

    public RunContext(String runId, Path baseDirectory) throws IOException {
        this.runId = runId;
        this.runDirectory = baseDirectory.resolve(runId);
        Files.createDirectories(runDirectory);
    }

    public static RunContext of(String experiment, String commit, Path baseDirectory) throws IOException {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String runId = timestamp + "-" + experiment + "-" + commit;
        return new RunContext(runId, baseDirectory);
    }

    public static RunContext fromPath(Path runDirectory) throws IOException {
        Files.createDirectories(runDirectory);
        return new RunContext(runDirectory.getFileName().toString(), runDirectory.getParent());
    }

    public String runId() {
        return runId;
    }

    public Path runDirectory() {
        return runDirectory;
    }

    public Path resolve(String relativePath) throws IOException {
        Path path = runDirectory.resolve(relativePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return path;
    }

    public Path loadDirectory() throws IOException {
        Path loadDir = runDirectory.resolve("load");
        Files.createDirectories(loadDir);
        return loadDir;
    }

    public Path jmhDirectory() throws IOException {
        Path jmhDir = runDirectory.resolve("jmh");
        Files.createDirectories(jmhDir);
        return jmhDir;
    }

    public Path explainDirectory() throws IOException {
        Path explainDir = runDirectory.resolve("explain");
        Files.createDirectories(explainDir);
        return explainDir;
    }
}
