package org.ulpgc.tarantino.bench.recovery;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The directory name of a recovery run (SPEC §9): {@code <timestamp>-recovery-<nn>-r<N>-<commit>} in mode
 * {@code hazelcast} and {@code <timestamp>-recovery-<nn>-<mode>-r<N>-<commit>} in any other.
 */
public record RecoveryRunName(String row, String mode, int replicas) {

    public static final String DEFAULT_MODE = "hazelcast";

    private static final Pattern NAME = Pattern.compile("\\d{8}-\\d{6}-recovery-(\\d{2})-(?:([a-z][a-z-]*)-)?r(\\d+)-[0-9a-f]+");

    public static Optional<RecoveryRunName> parse(String directoryName) {
        Matcher name = NAME.matcher(directoryName);
        if (!name.matches()) {
            return Optional.empty();
        }
        String mode = name.group(2) == null ? DEFAULT_MODE : name.group(2);
        return Optional.of(new RecoveryRunName(name.group(1), mode, Integer.parseInt(name.group(3))));
    }
}
