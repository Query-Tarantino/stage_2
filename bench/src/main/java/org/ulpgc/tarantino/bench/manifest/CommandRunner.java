package org.ulpgc.tarantino.bench.manifest;

import java.util.List;

/** Runs a host command, such as {@code git} or {@code docker}, and answers what it printed. */
@FunctionalInterface
public interface CommandRunner {

    String output(List<String> command);
}
