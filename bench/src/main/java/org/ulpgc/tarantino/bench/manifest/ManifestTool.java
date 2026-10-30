package org.ulpgc.tarantino.bench.manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.RunContext;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Opens a run on the host once the stack is up, printing its {@code run_id} for the script, and closes it at the end
 * (SPEC §9). It runs on the host, not in the {@code bench} container, because only there it sees git, Docker and the
 * real machine.
 */
public final class ManifestTool {

    static final int DONE = 0;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestTool.class);
    private static final String COMMAND_SEPARATOR = "--";
    private static final int CLIENT_CONCURRENCY = 3;
    private static final int ARGUMENTS_BEFORE_COMMAND = 4;
    private static final String USAGE = """
            Usage: ManifestTool start <runs directory> <experiment> <client concurrency> -- <command and its arguments>
                   ManifestTool finish <run directory>""";

    private ManifestTool() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    public static int run(List<String> args) {
        return run(args, new ProcessCommandRunner(), System.out);
    }

    static int run(List<String> args, CommandRunner runner, PrintStream runIdOutput) {
        if (isStart(args)) {
            Manifest manifest = start(Path.of(args.get(1)), args.get(2), Integer.parseInt(args.get(CLIENT_CONCURRENCY)),
                    args.subList(ARGUMENTS_BEFORE_COMMAND + 1, args.size()), runner);
            runIdOutput.println(manifest.runId());
            return DONE;
        }
        if (args.size() == 2 && args.getFirst().equals("finish")) {
            finish(Path.of(args.get(1)));
            return DONE;
        }
        LOGGER.error(USAGE);
        return WRONG_USAGE;
    }

    private static boolean isStart(List<String> args) {
        return args.size() > ARGUMENTS_BEFORE_COMMAND && args.getFirst().equals("start")
                && args.get(CLIENT_CONCURRENCY).matches("\\d+") && args.get(ARGUMENTS_BEFORE_COMMAND).equals(COMMAND_SEPARATOR);
    }

    private static Manifest start(Path runsDirectory, String experiment, int clientConcurrency, List<String> command, CommandRunner runner) {
        GitState git = GitState.of(runner);
        RunContext run = runContext(experiment, git.commit(), runsDirectory);
        Manifest manifest = new Manifest(run.runId(), git, List.copyOf(command), Instant.now(), null,
                HostInfo.of(runner, runsDirectory), DockerState.of(runner), ServiceConfiguration.ofComposeServices(runner), clientConcurrency);
        LOGGER.info("Started run {} in {}", manifest.runId(), manifest.writeTo(run.runDirectory()));
        return manifest;
    }

    private static void finish(Path runDirectory) {
        Manifest finished = Manifest.readFrom(runDirectory).withEnd(Instant.now());
        LOGGER.info("Finished run {} in {}", finished.runId(), finished.writeTo(runDirectory));
    }

    private static RunContext runContext(String experiment, String commit, Path runsDirectory) {
        try {
            return RunContext.of(experiment, commit, runsDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
