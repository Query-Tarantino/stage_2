package org.ulpgc.tarantino.bench.manifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProcessCommandRunner implements CommandRunner {

    @Override
    public String output(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " exited with " + process.exitValue());
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
