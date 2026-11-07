package org.ulpgc.tarantino.bench.ingest;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A transition line (SPEC §4.8) with the worker that wrote it: the replica of its {@code replica} key and the thread
 * that {@code slf4j-simple} writes in brackets before the level, as in {@code [download-3] INFO}.
 */
record WorkerLine(String worker, TransitionLine transition) {

    private static final Pattern THREAD = Pattern.compile("\\[([^\\]]+)] INFO ");
    private static final Pattern REPLICA = Pattern.compile("replica=(\\S+)");
    private static final String SEPARATOR = "/";

    static Optional<WorkerLine> parse(String logLine) {
        Matcher thread = THREAD.matcher(logLine);
        Matcher replica = REPLICA.matcher(logLine);
        if (!thread.find() || !replica.find()) {
            return Optional.empty();
        }
        String worker = replica.group(1) + SEPARATOR + thread.group(1);
        return TransitionLine.parse(logLine).map(transition -> new WorkerLine(worker, transition));
    }
}
