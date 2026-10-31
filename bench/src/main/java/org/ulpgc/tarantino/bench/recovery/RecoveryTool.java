package org.ulpgc.tarantino.bench.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.load.SearchLoadGenerator;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The recovery commands of {@code bench} (SPEC §8), run in the Compose {@code bench} service against nginx and MongoDB
 * with the timing of {@code control}: {@code wait <state|queue> [<minimum>]}, {@code recovery <nn> <run_id> <failed_at>} and
 * {@code recovery-compare <nn>}; and {@code instant [<seconds from now>]}, which gives the scripts their {@code failed_at}.
 */
public final class RecoveryTool {

    public static final int DONE = 0;
    public static final int FAILED = 1;
    public static final int WRONG_USAGE = 2;

    static final Path RUNS = Path.of("benchmarks", "runs");

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryTool.class);
    private static final Pattern SCRIPT = Pattern.compile("\\d{2}");
    private static final Pattern POSITIVE = Pattern.compile("[1-9]\\d{0,8}");
    private static final int WAIT_ARGUMENTS = 3;
    private static final Pattern RECOVERY_EXPERIMENT = Pattern.compile("recovery-\\d{2}-r\\d+");
    private static final String ZOMBIE_INDEXER_SCRIPT = "10";
    private static final String QUERY_REPLICA_SCRIPT = "14";
    private static final String RECOVERY_SECONDS = "recovery_seconds";
    private static final String SECONDS = "s";

    private RecoveryTool() {
    }

    public static int printInstant(List<String> args) {
        Optional<Instant> instant = InstantCommand.instant(args, Clock.systemUTC());
        if (instant.isEmpty()) {
            LOGGER.error("Usage: bench instant [<whole seconds from now>]");
            return WRONG_USAGE;
        }
        System.out.println(instant.get());
        return DONE;
    }

    public static int waitFor(List<String> args) {
        Optional<Integer> minimum = args.size() > 1 ? positive(args.get(1)) : Optional.of(WaitCommand.ANY);
        Optional<Duration> timeout = args.size() > 2 ? positive(args.get(2)).map(Duration::ofSeconds) : Optional.of(WaitCommand.DEFAULT_TIMEOUT);
        if (args.isEmpty() || args.size() > WAIT_ARGUMENTS || minimum.isEmpty() || timeout.isEmpty() || WaitCommand.sectionOf(args.getFirst()).isEmpty()) {
            LOGGER.error("Usage: bench wait <QUEUED|DOWNLOADING|DOWNLOADED|INDEXING|PUBLISHED|FAILED|books-to-download|books-to-index> [<minimum above 0> [<timeout seconds>]]");
            return WRONG_USAGE;
        }
        boolean reached = new WaitCommand(nginx(), Clock.systemUTC(), Pause.ofThread()).awaitAtLeast(args.getFirst(), minimum.get(), timeout.get());
        LOGGER.info(reached ? "{} reached {}" : "{} stayed below {} for {} s", args.getFirst(), minimum.get(), timeout.get().toSeconds());
        return reached ? DONE : FAILED;
    }

    public static int recovery(List<String> args) {
        Optional<Instant> failedAt = args.size() == 3 ? instant(args.get(2)) : Optional.empty();
        if (failedAt.isEmpty() || !SCRIPT.matcher(args.getFirst()).matches()) {
            LOGGER.error("Usage: bench recovery <nn> <run_id> <failed_at as ISO-8601 UTC>");
            return WRONG_USAGE;
        }
        return recover(args.getFirst(), RUNS.resolve(args.get(1)), failedAt.get());
    }

    public static int compare(List<String> args) {
        if (args.size() != 1 || !SCRIPT.matcher(args.getFirst()).matches()) {
            LOGGER.error("Usage: bench recovery-compare <nn>");
            return WRONG_USAGE;
        }
        List<String> differences = new RecoveryComparison(RUNS).differences(args.getFirst());
        differences.forEach(LOGGER::error);
        return differences.isEmpty() ? DONE : FAILED;
    }

    private static int recover(String script, Path runDirectory, Instant failedAt) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.CONTROL);
        Stack stack = nginx();
        List<String> queries = queries(config.workload().resolve("queries.txt"));
        Optional<Instant> abandonedPostingsGoneBy = script.equals(ZOMBIE_INDEXER_SCRIPT) ? Optional.of(failedAt.plus(config.gc())) : Optional.empty();
        RecoveryPass pass = new RecoveryPass(stack, new MongoRecoveryReads(MongoDatabases.database(config.mongoUri())),
                new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords()),
                Clock.systemUTC(), abandonedPostingsGoneBy);
        Optional<RecoveryChecks> checks = new RecoveryCommand(pass::failures, stack, queries, Clock.systemUTC(), Pause.ofThread())
                .awaitRecovery(failedAt);
        if (checks.isEmpty() || !searchesKeptAnswering(script, stack, queries)) {
            return FAILED;
        }
        LOGGER.info("Recovered in {} s; checks in {}", checks.get().recoverySeconds(), checks.get().writeTo(runDirectory));
        appendRecoverySeconds(runDirectory, checks.get().recoverySeconds());
        return DONE;
    }

    private static boolean searchesKeptAnswering(String script, Stack stack, List<String> queries) {
        if (!script.equals(QUERY_REPLICA_SCRIPT)) {
            return true;
        }
        Optional<String> failure = new ContinuousSearch(stack, queries, Clock.systemUTC(), Pause.ofThread()).failure();
        failure.ifPresent(LOGGER::error);
        return failure.isEmpty();
    }

    private static void appendRecoverySeconds(Path runDirectory, double recoverySeconds) {
        SummaryStats.Metrics single = SummaryStats.calculate(List.of(recoverySeconds));
        try {
            SummaryStats.appendToSummary(runDirectory.resolve(SummaryStats.FILE_NAME), RECOVERY_SECONDS, configuration(runDirectory), single, SECONDS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String configuration(Path runDirectory) {
        Matcher experiment = RECOVERY_EXPERIMENT.matcher(runDirectory.getFileName().toString());
        return experiment.find() ? experiment.group() : runDirectory.getFileName().toString();
    }

    private static List<String> queries(Path file) {
        try {
            return SearchLoadGenerator.loadQueries(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<Integer> positive(String value) {
        return POSITIVE.matcher(value).matches() ? Optional.of(Integer.parseInt(value)) : Optional.empty();
    }

    private static Optional<Instant> instant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Stack nginx() {
        return new HttpStack(HttpStack.FRONT_END, HttpStack.OPERATIONS);
    }
}
