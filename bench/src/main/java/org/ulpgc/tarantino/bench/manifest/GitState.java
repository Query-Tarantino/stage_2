package org.ulpgc.tarantino.bench.manifest;

import java.util.List;

/** The commit a run measures and whether the tree was clean (SPEC §9). */
public record GitState(String commit, boolean clean) {

    static final List<String> SHORT_COMMIT = List.of("git", "rev-parse", "--short", "HEAD");
    static final List<String> CHANGED_FILES = List.of("git", "status", "--porcelain");

    public static GitState of(CommandRunner runner) {
        return new GitState(runner.output(SHORT_COMMIT).strip(), runner.output(CHANGED_FILES).isBlank());
    }
}
