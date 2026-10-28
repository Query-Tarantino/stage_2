package org.ulpgc.tarantino.bench;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.containers.StatsTool;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;
import org.ulpgc.tarantino.bench.corpus.CorpusTool;
import org.ulpgc.tarantino.bench.explain.ExplainPlanExporter;
import org.ulpgc.tarantino.bench.jmh.JmhCsvConverter;
import org.ulpgc.tarantino.bench.load.SearchLoadGenerator;
import org.ulpgc.tarantino.bench.manifest.ManifestTool;
import org.ulpgc.tarantino.bench.recovery.RecoveryTool;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * The single entry point of the {@code bench} jar: {@code bench <command> <arguments>}, as the scripts run it with
 * {@code docker compose run --rm bench <command>} (SPEC §2.4 and §8) or with {@code java -jar} on the host.
 */
public final class Bench {

    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(Bench.class);
    private static final Map<String, ToIntFunction<List<String>>> COMMANDS = new TreeMap<>(Map.ofEntries(
            Map.entry("load", ofMain(SearchLoadGenerator::main)),
            Map.entry("jmh-csv", ofMain(JmhCsvConverter::main)),
            Map.entry("summary", ofMain(SummaryStats::main)),
            Map.entry("explain", ofMain(ExplainPlanExporter::main)),
            Map.entry("corpus", CorpusTool::run),
            Map.entry("manifest", ManifestTool::run),
            Map.entry("stats", StatsTool::run),
            Map.entry("coordination", CoordinationTool::run),
            Map.entry("wait", RecoveryTool::waitFor),
            Map.entry("recovery", RecoveryTool::recovery),
            Map.entry("recovery-compare", RecoveryTool::compare)));

    private Bench() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    static int run(List<String> args) {
        if (args.isEmpty() || !COMMANDS.containsKey(args.getFirst())) {
            LOGGER.error("Usage: bench <command> <arguments>, where the command is one of {}", COMMANDS.keySet());
            return WRONG_USAGE;
        }
        return COMMANDS.get(args.getFirst()).applyAsInt(args.subList(1, args.size()));
    }

    private static ToIntFunction<List<String>> ofMain(Consumer<String[]> main) {
        return args -> {
            main.accept(args.toArray(String[]::new));
            return 0;
        };
    }
}
