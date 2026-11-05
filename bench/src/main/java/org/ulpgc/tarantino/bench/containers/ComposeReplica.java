package org.ulpgc.tarantino.bench.containers;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A container of a Compose service, named {@code <project>-<service>-<replica>} by Compose. */
public record ComposeReplica(String service, int replica) {

    private static final Pattern NAME = Pattern.compile(".+-([a-z]+)-([1-9]\\d*)");

    /** Nothing for a container Compose did not name after a service, such as a {@code bench-run-<id>}. */
    public static Optional<ComposeReplica> of(String container) {
        Matcher name = NAME.matcher(container);
        return name.matches() ? Optional.of(new ComposeReplica(name.group(1), Integer.parseInt(name.group(2)))) : Optional.empty();
    }
}
