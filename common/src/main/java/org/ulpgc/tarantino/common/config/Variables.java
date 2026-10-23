package org.ulpgc.tarantino.common.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Environment variables where a blank value counts as unset, as {@code TARANTINO_MIRROR} did in Stage 1. */
final class Variables {

    private static final Pattern POSITIVE_INTEGER = Pattern.compile("[1-9][0-9]{0,8}");

    private final Map<String, String> environment;

    Variables(Map<String, String> environment) {
        this.environment = environment;
    }

    Optional<String> optionalText(String name) {
        return Optional.ofNullable(environment.get(name)).map(String::strip).filter(value -> !value.isEmpty());
    }

    String text(String name, String defaultValue) {
        return optionalText(name).orElse(defaultValue);
    }

    List<String> list(String name, String defaultValue) {
        List<String> values = Arrays.stream(text(name, defaultValue).split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
        if (values.isEmpty()) {
            throw new InvalidConfigurationException(name, "must list at least one value");
        }
        return values;
    }

    int positive(String name, int defaultValue) {
        return optionalText(name).map(value -> positive(name, value)).orElse(defaultValue);
    }

    OptionalInt optionalPositive(String name) {
        return optionalText(name).map(value -> OptionalInt.of(positive(name, value))).orElseGet(OptionalInt::empty);
    }

    Duration seconds(String name, int defaultValue) {
        return Duration.ofSeconds(positive(name, defaultValue));
    }

    private static int positive(String name, String value) {
        if (!POSITIVE_INTEGER.matcher(value).matches()) {
            throw new InvalidConfigurationException(name, "must be a positive integer, not " + value);
        }
        return Integer.parseInt(value);
    }
}
