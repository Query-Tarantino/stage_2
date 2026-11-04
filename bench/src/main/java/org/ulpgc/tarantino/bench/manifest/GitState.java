package org.ulpgc.tarantino.bench.manifest;

import java.util.List;

/**
 * The commit a run measures, whether the tree was clean and its branch (SPEC §9). The runs in {@code benchmarks/runs/} are what
 * the experiments write, not what they measure, so a run not yet committed leaves the tree clean.
 */
public record GitState(String commit, boolean clean, String branch) {

    static final String RUNS_PATHSPEC = ":(top,exclude)benchmarks/runs";
    static final List<String> SHORT_COMMIT = List.of("git", "rev-parse", "--short", "HEAD");
    static final List<String> CHANGED_FILES = List.of("git", "status", "--porcelain", "--", RUNS_PATHSPEC);
    static final List<String> BRANCH = List.of("git", "rev-parse", "--abbrev-ref", "HEAD");

    public static GitState of(CommandRunner runner) {
        return new GitState(runner.output(SHORT_COMMIT).strip(), runner.output(CHANGED_FILES).isBlank(),
                runner.output(BRANCH).strip());
    }
}
