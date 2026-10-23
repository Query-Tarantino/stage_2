package org.ulpgc.tarantino.crawler;

import java.nio.file.Path;

/** {@code mirror} is the local Gutenberg mirror to read books from, or null to download them over HTTP. */
public record CrawlerConfig(Path datalake, Path mirror) {

    public static CrawlerConfig fromEnvironment() {
        String mirror = variable("TARANTINO_MIRROR", "");
        return new CrawlerConfig(
                Path.of(variable("TARANTINO_DATALAKE", "datalake")),
                mirror.isBlank() ? null : Path.of(mirror));
    }

    private static String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
