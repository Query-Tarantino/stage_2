package org.ulpgc.tarantino.bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.jmh.JmhCsvConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmhCsvConverterTest {

    @Test
    void shouldConvertJmhJsonToCsv(@TempDir Path tempDir) throws IOException {
        String json = """
                [
                  {
                    "jmhVersion": "1.37",
                    "benchmark": "org.ulpgc.tarantino.query.benchmarks.SearchBenchmark.searchWithoutFilters",
                    "mode": "thrpt",
                    "threads": 1,
                    "forks": 1,
                    "jvm": "openjdk 25",
                    "jvmArgs": ["-Xmx1g"],
                    "warmupIterations": 5,
                    "measurementIterations": 10,
                    "primaryMetric": {
                      "score": 14250.1234,
                      "scoreError": 120.5678,
                      "scoreConfidence": [14129.5556, 14370.6912],
                      "scorePercentiles": {
                        "99.0": 14250.0
                      },
                      "scoreUnit": "ops/s"
                    }
                  },
                  {
                    "jmhVersion": "1.37",
                    "benchmark": "org.ulpgc.tarantino.query.benchmarks.SearchBenchmark.tfidfRanking",
                    "mode": "avgt",
                    "threads": 1,
                    "forks": 1,
                    "jvm": "openjdk 25",
                    "jvmArgs": ["-Xmx1g"],
                    "warmupIterations": 5,
                    "measurementIterations": 10,
                    "primaryMetric": {
                      "score": 0.0456,
                      "scoreError": 0.0012,
                      "scoreUnit": "ms/op"
                    }
                  }
                ]
                """;

        Path jsonFile = tempDir.resolve("query.json");
        Path csvFile = tempDir.resolve("query.csv");
        Files.writeString(jsonFile, json);

        JmhCsvConverter.convert(jsonFile, csvFile);

        assertTrue(Files.exists(csvFile));
        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(3, lines.size());
        assertEquals("\"Benchmark\",\"Mode\",\"Score\",\"Score Error (99.9%)\",\"Unit\",\"Forks\",\"Warmup Iterations\",\"Measurement Iterations\",\"JVM Args\"", lines.getFirst());
        assertTrue(lines.get(1).contains("\"org.ulpgc.tarantino.query.benchmarks.SearchBenchmark.searchWithoutFilters\",\"thrpt\",14250.1234,120.5678,\"ops/s\",1,5,10,\"-Xmx1g\""));
        assertTrue(lines.get(2).contains("\"org.ulpgc.tarantino.query.benchmarks.SearchBenchmark.tfidfRanking\",\"avgt\",0.0456,0.0012,\"ms/op\",1,5,10,\"-Xmx1g\""));
    }

    @Test
    void addsAColumnPerParamSoEachRowSaysWhichCombinationItMeasured() throws IOException {
        String json = """
                [
                  {"benchmark": "TokenizerBenchmark.tokenize", "mode": "avgt", "params": {"bookId": "1080"},
                   "primaryMetric": {"score": 1.5, "scoreError": 0.1, "scoreUnit": "ms/op"}},
                  {"benchmark": "TokenizerBenchmark.tokenize", "mode": "avgt", "params": {"bookId": "84"},
                   "primaryMetric": {"score": 2.5, "scoreError": 0.2, "scoreUnit": "ms/op"}},
                  {"benchmark": "BatchWriteBenchmark.write", "mode": "avgt", "params": {"books": "100"},
                   "primaryMetric": {"score": 30.0, "scoreError": 1.0, "scoreUnit": "ms/op"}}
                ]
                """;

        List<String> lines = JmhCsvConverter.convertJsonToCsv(json).lines().toList();

        assertTrue(lines.getFirst().endsWith(",\"JVM Args\",\"Param: bookId\",\"Param: books\""));
        assertTrue(lines.get(1).startsWith("\"TokenizerBenchmark.tokenize\",\"avgt\",1.5000,"));
        assertTrue(lines.get(1).endsWith(",\"1080\",\"\""));
        assertTrue(lines.get(2).endsWith(",\"84\",\"\""));
        assertTrue(lines.get(3).endsWith(",\"\",\"100\""));
    }
}
