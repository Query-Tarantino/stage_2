package org.ulpgc.tarantino.common.language;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * The {@code Language:} header names found in the cache and their ISO 639-1 codes (SPEC §4.3), and each code's
 * name for {@code GET /search/filters} (SPEC §6.4). A name outside the table has no code and is not translated.
 */
public final class LanguageCodes {

    private static final Map<String, String> CODES_BY_NAME = Map.ofEntries(
            entry("Bulgarian", "bg"),
            entry("Chinese", "zh"),
            entry("Danish", "da"),
            entry("Dutch", "nl"),
            entry("English", "en"),
            entry("French", "fr"),
            entry("German", "de"),
            entry("Italian", "it"),
            entry("Japanese", "ja"),
            entry("Latin", "la"),
            entry("Portuguese", "pt"),
            entry("Spanish", "es"),
            entry("Swedish", "sv"),
            entry("Welsh", "cy"));

    private static final Map<String, String> NAMES_BY_CODE = CODES_BY_NAME.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private LanguageCodes() {
    }

    public static Optional<String> code(String name) {
        return Optional.ofNullable(CODES_BY_NAME.get(name));
    }

    public static Optional<String> name(String code) {
        return Optional.ofNullable(NAMES_BY_CODE.get(code));
    }
}
