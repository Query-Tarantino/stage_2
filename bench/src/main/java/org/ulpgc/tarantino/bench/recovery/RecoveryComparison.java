package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@code bench recovery-compare <nn>} (SPEC §8): the runs of a script, whatever their replicas, must end with the same
 * {@code books} in {@code GET /control/status} and the same {@code /search} answers. Fewer than two runs with their
 * {@code checks.json} compare nothing, which is a failure too.
 */
public class RecoveryComparison {

    private static final int RUNS_TO_COMPARE = 2;

    private final Path runsDirectory;

    public RecoveryComparison(Path runsDirectory) {
        this.runsDirectory = runsDirectory;
    }

    /** What differs between the runs of {@code script}, by run directory name; nothing when they all agree. */
    public List<String> differences(String script) {
        List<Path> runs = runsOf(script);
        if (runs.size() < RUNS_TO_COMPARE) {
            return List.of("Only " + runs.size() + " runs of script " + script + " have their " + RecoveryChecks.FILE_NAME);
        }
        Path reference = runs.getFirst();
        JsonNode expected = checks(reference);
        return runs.stream().skip(1)
                .flatMap(run -> differences(name(reference), expected, name(run), checks(run)))
                .toList();
    }

    private Stream<String> differences(String referenceName, JsonNode expected, String runName, JsonNode actual) {
        Optional<String> books = expected.path("control_status").path("books").equals(actual.path("control_status").path("books"))
                ? Optional.empty()
                : Optional.of(runName + " has other books than " + referenceName + ": " + actual.path("control_status").path("books"));
        Stream<String> searches = StreamSupport.stream(expected.path("search").properties().spliterator(), false)
                .map(Map.Entry::getKey)
                .filter(query -> !expected.path("search").path(query).equals(actual.path("search").path(query)))
                .map(query -> runName + " answers /search?q=" + query + " unlike " + referenceName);
        return Stream.concat(books.stream(), searches);
    }

    private List<Path> runsOf(String script) {
        Pattern runName = Pattern.compile("\\d{8}-\\d{6}-recovery-" + Pattern.quote(script) + "-r\\d+-[0-9a-f]+");
        try (Stream<Path> runs = Files.list(runsDirectory)) {
            return runs.filter(run -> runName.matcher(name(run)).matches())
                    .filter(run -> Files.exists(run.resolve(RecoveryChecks.FILE_NAME)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode checks(Path run) {
        try {
            return TarantinoJson.mapper().readTree(Files.readString(run.resolve(RecoveryChecks.FILE_NAME)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(Path run) {
        return run.getFileName().toString();
    }
}
