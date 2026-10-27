package org.ulpgc.tarantino.bench.jmh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class JmhCsvConverter {

    private static final ObjectMapper MAPPER = TarantinoJson.mapper();

    public static Path convert(Path jsonFile, Path csvFile) throws IOException {
        String jsonContent = Files.readString(jsonFile, StandardCharsets.UTF_8);
        String csvContent = convertJsonToCsv(jsonContent);
        if (csvFile.getParent() != null) {
            Files.createDirectories(csvFile.getParent());
        }
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return csvFile;
    }

    public static String convertJsonToCsv(String jsonContent) throws IOException {
        JsonNode root = MAPPER.readTree(jsonContent);
        StringBuilder builder = new StringBuilder();
        builder.append("\"Benchmark\",\"Mode\",\"Score\",\"Score Error (99.9%)\",\"Unit\"\n");

        if (root.isArray()) {
            for (JsonNode benchmarkNode : root) {
                String benchmarkName = benchmarkNode.path("benchmark").asText("");
                String benchmarkMode = benchmarkNode.path("mode").asText("");
                JsonNode primaryMetric = benchmarkNode.path("primaryMetric");
                double score = primaryMetric.path("score").asDouble(0.0);
                double scoreError = primaryMetric.path("scoreError").asDouble(0.0);
                String scoreUnit = primaryMetric.path("scoreUnit").asText("");

                builder.append(String.format(Locale.ROOT, "\"%s\",\"%s\",%.4f,%.4f,\"%s\"\n",
                        benchmarkName, benchmarkMode, score, scoreError, scoreUnit));
            }
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: JmhCsvConverter <input-json> <output-csv>");
            System.exit(1);
        }
        try {
            convert(Paths.get(args[0]), Paths.get(args[1]));
        } catch (Exception exception) {
            System.err.println("Failed to convert JMH JSON to CSV: " + exception.getMessage());
            System.exit(1);
        }
    }
}
