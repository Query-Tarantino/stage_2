package org.ulpgc.tarantino.bench.run;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The name of a trial in a run with trials (SPEC §9), {@code <factor>-<value>-...-rep-<n>}, which also names its
 * {@code logs/<trial>/} folder.
 */
public final class TrialName {

    /** Trials by the value of each factor, numerically when both values are numbers, and then by repetition. */
    public static final Comparator<String> ORDER = Comparator.comparing(TrialName::sortKeys, TrialName::compareKeys);

    private static final Pattern NAME = Pattern.compile("([a-z0-9]+-[a-z0-9]+(?:-[a-z0-9]+-[a-z0-9]+)*)-rep-([1-9]\\d*)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final int FACTORS = 1;
    private static final int REPETITION = 2;
    private static final String WORD_SEPARATOR = "-";
    private static final String ASSIGNMENT = "=";
    private static final String FACTOR_SEPARATOR = ",";

    private TrialName() {
    }

    public static boolean isValid(String trial) {
        return NAME.matcher(trial).matches();
    }

    /** The factors the trial set, in name order: {@code replicas-2-workers-8-rep-1} is {@code {replicas=2, workers=8}}. */
    public static Map<String, String> factors(String trial) {
        String[] words = matched(trial).group(FACTORS).split(WORD_SEPARATOR);
        return IntStream.range(0, words.length / 2).boxed().collect(Collectors.toMap(factor -> words[2 * factor],
                factor -> words[2 * factor + 1], (first, second) -> first, LinkedHashMap::new));
    }

    public static int repetition(String trial) {
        return Integer.parseInt(matched(trial).group(REPETITION));
    }

    /** The configuration the trial measured, as {@code summary.csv} names it: {@code replicas-2-workers-8-rep-1} is {@code replicas=2,workers=8}. */
    public static String configuration(String trial) {
        return factors(trial).entrySet().stream()
                .map(factor -> factor.getKey() + ASSIGNMENT + factor.getValue())
                .collect(Collectors.joining(FACTOR_SEPARATOR));
    }

    private static Matcher matched(String trial) {
        Matcher name = NAME.matcher(trial);
        if (!name.matches()) {
            throw new IllegalArgumentException("Not a trial name: " + trial);
        }
        return name;
    }

    private static List<String> sortKeys(String trial) {
        Matcher name = matched(trial);
        return Stream.concat(Stream.of(name.group(FACTORS).split(WORD_SEPARATOR)), Stream.of(name.group(REPETITION))).toList();
    }

    private static int compareKeys(List<String> first, List<String> second) {
        return IntStream.range(0, Math.min(first.size(), second.size()))
                .map(index -> compareWords(first.get(index), second.get(index)))
                .filter(comparison -> comparison != 0)
                .findFirst()
                .orElse(Integer.compare(first.size(), second.size()));
    }

    private static int compareWords(String first, String second) {
        boolean numbers = NUMBER.matcher(first).matches() && NUMBER.matcher(second).matches();
        return numbers ? new BigInteger(first).compareTo(new BigInteger(second)) : first.compareTo(second);
    }
}
