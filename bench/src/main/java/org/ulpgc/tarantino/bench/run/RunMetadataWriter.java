package org.ulpgc.tarantino.bench.run;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class RunMetadataWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunMetadataWriter.class);

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    public static void verifyCorpus(Path corpusShaFile) throws IOException {
        if (!Files.exists(corpusShaFile)) {
            throw new IllegalStateException("Corpus checksum file not found: " + corpusShaFile);
        }
        try (var lines = Files.lines(corpusShaFile, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(RunMetadataWriter::verifyCorpusLine);
        }
    }

    private static void verifyCorpusLine(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) {
            return;
        }
        String firstColumn = tokens[0];
        String secondColumn = tokens[1];
        String expectedHash;
        Path targetPath;
        if (firstColumn.matches("^[a-fA-F0-9]{64}$")) {
            expectedHash = firstColumn.toLowerCase(Locale.ROOT);
            targetPath = resolveCorpusFilePath(secondColumn);
        } else if (secondColumn.matches("^[a-fA-F0-9]{64}$")) {
            expectedHash = secondColumn.toLowerCase(Locale.ROOT);
            targetPath = resolveCorpusIdPath(firstColumn);
        } else {
            return;
        }

        if (!Files.exists(targetPath)) {
            throw new IllegalStateException("Missing corpus file: " + targetPath);
        }
        try {
            String actualHash = sha256(targetPath);
            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IllegalStateException("SHA-256 mismatch for " + targetPath
                        + " (expected " + expectedHash + ", got " + actualHash + ")");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to calculate SHA-256 for " + targetPath, exception);
        }
    }

    private static Path resolveCorpusFilePath(String pathString) {
        Path direct = Paths.get(pathString);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parentRelative = Paths.get("..").resolve(pathString);
        if (Files.exists(parentRelative)) {
            return parentRelative;
        }
        return direct;
    }

    private static Path resolveCorpusIdPath(String bookIdString) {
        List<Path> candidates = List.of(
                Paths.get("benchmarks/mirror/" + bookIdString + "/pg" + bookIdString + ".txt"),
                Paths.get("benchmarks/cache/" + bookIdString + ".txt"),
                Paths.get("../benchmarks/mirror/" + bookIdString + "/pg" + bookIdString + ".txt"),
                Paths.get("../benchmarks/cache/" + bookIdString + ".txt")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.getFirst());
    }

    public static void writeCorpusJson(Path runDir, Path corpusShaFile, String mongoUri, int generation) throws IOException {
        Files.createDirectories(runDir);
        List<Integer> bookIds = resolveCorpusIds(corpusShaFile);
        String fileHash = Files.exists(corpusShaFile) ? sha256(corpusShaFile) : "unknown";
        int termsCount = resolveDistinctTerms(mongoUri, generation);

        ObjectNode root = TarantinoJson.mapper().createObjectNode();
        ArrayNode idsArray = root.putArray("ids");
        bookIds.forEach(idsArray::add);
        root.put("sha256", fileHash);
        root.put("books", bookIds.size());
        root.put("terms", termsCount);

        TarantinoJson.mapper().writerWithDefaultPrettyPrinter().writeValue(runDir.resolve("corpus.json").toFile(), root);
    }

    static List<Integer> resolveCorpusIds(Path corpusShaFile) {
        if (Files.exists(corpusShaFile)) {
            try (var lines = Files.lines(corpusShaFile, StandardCharsets.UTF_8)) {
                List<Integer> ids = lines.map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(RunMetadataWriter::extractBookId)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
                if (!ids.isEmpty()) {
                    return ids;
                }
            } catch (IOException exception) {
            }
        }
        Path sampleIdsPath = Paths.get("workload/sample_ids.txt");
        if (!Files.exists(sampleIdsPath)) {
            sampleIdsPath = Paths.get("../workload/sample_ids.txt");
        }
        if (Files.exists(sampleIdsPath)) {
            try (var lines = Files.lines(sampleIdsPath, StandardCharsets.UTF_8)) {
                return lines.map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(Integer::parseInt)
                        .toList();
            } catch (IOException exception) {
            }
        }
        return List.of();
    }

    private static Optional<Integer> extractBookId(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) {
            return Optional.empty();
        }
        if (tokens[0].matches("^[0-9]+$")) {
            return Optional.of(Integer.parseInt(tokens[0]));
        }
        if (tokens.length > 1 && tokens[1].matches("^[0-9]+$")) {
            return Optional.of(Integer.parseInt(tokens[1]));
        }
        return Optional.empty();
    }

    static int resolveDistinctTerms(String mongoUri, int generation) {
        if (mongoUri == null || mongoUri.isBlank()) {
            return 0;
        }
        try {
            MongoDatabase database = MongoDatabases.database(mongoUri);
            return database.getCollection(MongoCollections.postings(generation))
                    .distinct("term", String.class)
                    .into(new HashSet<>())
                    .size();
        } catch (Exception exception) {
            return 0;
        }
    }

    public static void writeManifest(
            Path runDir,
            String runId,
            String experiment,
            String command,
            String startedAt,
            String finishedAt,
            String clientsSeries,
            String threadsSeries,
            String replicasSeries) throws IOException {
        Files.createDirectories(runDir);

        ObjectNode root = TarantinoJson.mapper().createObjectNode();
        root.put("run_id", runId);
        root.put("experiment", experiment);
        root.put("command", command);
        root.put("started_at", startedAt);
        if (finishedAt != null && !finishedAt.isBlank()) {
            root.put("finished_at", finishedAt);
        } else {
            root.putNull("finished_at");
        }
        root.put("commit", resolveGitCommit());
        root.put("clean_tree", resolveCleanTree());

        ObjectNode hostNode = root.putObject("host");
        hostNode.put("cpu", resolveCpuName());
        hostNode.put("cores", Runtime.getRuntime().availableProcessors());
        hostNode.put("memory_mb", resolveTotalMemoryMb());
        long diskBytes = runDir.toFile().getTotalSpace();
        hostNode.put("disk_gb", diskBytes / (1024L * 1024L * 1024L));
        hostNode.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));

        ObjectNode dockerNode = root.putObject("docker");
        dockerNode.put("version", resolveDockerVersion());
        ObjectNode dockerResources = dockerNode.putObject("resources");
        dockerResources.put("cpus", Runtime.getRuntime().availableProcessors());
        dockerResources.put("memory_mb", resolveTotalMemoryMb());

        ObjectNode jvmNode = root.putObject("jvm");
        jvmNode.put("version", System.getProperty("java.version"));
        ArrayNode flagsArray = jvmNode.putArray("flags");
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(flagsArray::add);

        ObjectNode variablesNode = root.putObject("variables");
        System.getenv().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("TARANTINO_"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> variablesNode.put(entry.getKey(), entry.getValue()));

        ArrayNode replicasArray = root.putArray("replicas");
        parseIntegerSeries(replicasSeries, List.of(1, 2, 3)).forEach(replicasArray::add);

        ArrayNode threadsArray = root.putArray("threads");
        parseIntegerSeries(threadsSeries, List.of(8, 32, 128)).forEach(threadsArray::add);

        ArrayNode concurrencyArray = root.putArray("concurrency");
        parseIntegerSeries(clientsSeries, List.of(1, 8, 32, 64)).forEach(concurrencyArray::add);

        TarantinoJson.mapper().writerWithDefaultPrettyPrinter().writeValue(runDir.resolve("manifest.json").toFile(), root);
    }

    public static List<Integer> parseIntegerSeries(String seriesString, List<Integer> fallback) {
        if (seriesString == null || seriesString.isBlank()) {
            return fallback;
        }
        return Arrays.stream(seriesString.trim().split("[,\\s]+"))
                .filter(token -> !token.isBlank())
                .map(Integer::parseInt)
                .toList();
    }

    static String resolveGitCommit() {
        String envCommit = System.getenv("GIT_COMMIT");
        if (envCommit != null && !envCommit.isBlank()) {
            return envCommit;
        }
        return runCommand("git", "rev-parse", "HEAD").orElse("unknown");
    }

    static boolean resolveCleanTree() {
        return runCommand("git", "status", "--porcelain")
                .map(String::isBlank)
                .orElse(false);
    }

    static String resolveDockerVersion() {
        return runCommand("docker", "--version").orElse("unknown");
    }

    static String resolveCpuName() {
        String identifier = System.getenv("PROCESSOR_IDENTIFIER");
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        Path cpuInfo = Paths.get("/proc/cpuinfo");
        if (Files.exists(cpuInfo)) {
            try (var lines = Files.lines(cpuInfo)) {
                Optional<String> model = lines.filter(line -> line.startsWith("model name"))
                        .map(line -> line.substring(line.indexOf(':') + 1).trim())
                        .findFirst();
                if (model.isPresent()) {
                    return model.get();
                }
            } catch (IOException exception) {
            }
        }
        return System.getProperty("os.arch", "unknown");
    }

    static long resolveTotalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                return sunBean.getTotalMemorySize() / (1024L * 1024L);
            }
        } catch (Exception exception) {
        }
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    private static Optional<String> runCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            return exitCode == 0 ? Optional.of(output) : Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: RunMetadataWriter <verify-corpus|write-manifest|write-corpus> [options...]");
            System.exit(1);
        }
        try {
            String subcommand = args[0];
            switch (subcommand) {
                case "verify-corpus" -> {
                    Path corpusSha = Paths.get(args.length > 1 ? args[1] : "workload/corpus.sha256");
                    verifyCorpus(corpusSha);
                    System.out.println("Corpus checksums verified successfully");
                }
                case "write-manifest" -> {
                    Path runDir = Paths.get(args.length > 1 ? args[1] : "benchmarks/runs/latest");
                    String runId = args.length > 2 ? args[2] : "unknown-run";
                    String experiment = args.length > 3 ? args[3] : "search-latency";
                    String command = args.length > 4 ? args[4] : "";
                    String startedAt = args.length > 5 ? args[5] : "";
                    String finishedAt = args.length > 6 ? args[6] : "";
                    String clientsSeries = args.length > 7 ? args[7] : System.getenv().getOrDefault("TARANTINO_CLIENTS_LIST", "1 8 32 64");
                    String threadsSeries = args.length > 8 ? args[8] : System.getenv().getOrDefault("TARANTINO_THREADS_LIST", "8 32 128");
                    String replicasSeries = args.length > 9 ? args[9] : System.getenv().getOrDefault("TARANTINO_REPLICAS_LIST", "1 2 3");
                    writeManifest(runDir, runId, experiment, command, startedAt, finishedAt, clientsSeries, threadsSeries, replicasSeries);
                }
                case "write-corpus" -> {
                    Path runDir = Paths.get(args.length > 1 ? args[1] : "benchmarks/runs/latest");
                    Path corpusSha = Paths.get(args.length > 2 ? args[2] : "workload/corpus.sha256");
                    String mongoUri = args.length > 3 ? args[3] : System.getenv().getOrDefault("TARANTINO_MONGO_URI", "");
                    int generation = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                    writeCorpusJson(runDir, corpusSha, mongoUri, generation);
                }
                default -> throw new IllegalArgumentException("Unknown subcommand: " + subcommand);
            }
        } catch (Exception exception) {
            System.err.println("Error: " + exception.getMessage());
            exception.printStackTrace();
            System.exit(1);
        }
    }
}
