package org.ulpgc.tarantino.bench.run;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The name of a trial in a run with trials (SPEC §9), {@code <factor>-<value>-...-rep-<n>}, which also names its
 * {@code logs/<trial>/} folder.
 */
public final class TrialName {

    private static final Pattern NAME = Pattern.compile("([a-z0-9]+-[a-z0-9]+(?:-[a-z0-9]+-[a-z0-9]+)*)-rep-[1-9]\\d*");
    private static final String WORD_SEPARATOR = "-";
    private static final String ASSIGNMENT = "=";
    private static final String FACTOR_SEPARATOR = ",";

    private TrialName() {
    }

    public static boolean isValid(String trial) {
        return NAME.matcher(trial).matches();
    }

    /** The configuration the trial measured, as {@code summary.csv} names it: {@code replicas-2-workers-8-rep-1} is {@code replicas=2,workers=8}. */
    public static String configuration(String trial) {
        Matcher name = NAME.matcher(trial);
        if (!name.matches()) {
            throw new IllegalArgumentException("Not a trial name: " + trial);
        }
        String[] words = name.group(1).split(WORD_SEPARATOR);
        return IntStream.range(0, words.length / 2)
                .mapToObj(factor -> words[2 * factor] + ASSIGNMENT + words[2 * factor + 1])
                .collect(Collectors.joining(FACTOR_SEPARATOR));
    }
}
