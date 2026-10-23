package org.ulpgc.tarantino.query.benchmarking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ReferenceResults {

    private static final String QUERY_SEPARATOR = "\t";
    private static final String ID_SEPARATOR = ",";

    private ReferenceResults() {
    }

    static Map<String, List<Integer>> read(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(line -> line.split(QUERY_SEPARATOR, -1))
                    .collect(Collectors.toMap(fields -> fields[0], fields -> ids(fields[1])));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void write(Path file, Map<String, List<Integer>> results) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, results.entrySet().stream().map(ReferenceResults::line).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String line(Map.Entry<String, List<Integer>> result) {
        return result.getKey() + QUERY_SEPARATOR + result.getValue().stream().map(String::valueOf).collect(Collectors.joining(ID_SEPARATOR));
    }

    private static List<Integer> ids(String text) {
        return text.isEmpty() ? List.of() : Arrays.stream(text.split(ID_SEPARATOR)).map(Integer::valueOf).toList();
    }
}
