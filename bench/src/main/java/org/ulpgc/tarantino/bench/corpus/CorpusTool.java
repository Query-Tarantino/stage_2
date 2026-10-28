package org.ulpgc.tarantino.bench.corpus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates {@code workload/corpus.sha256} once from the cache, and before each run checks the cache against it and
 * writes the run's {@code corpus.json} (SPEC §9).
 */
public final class CorpusTool {

    static final int DONE = 0;
    static final int CORPUS_DIFFERS = 1;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(CorpusTool.class);
    private static final String USAGE = """
            Usage: CorpusTool generate <cache directory> <corpus.sha256>
                   CorpusTool check <cache directory> <corpus.sha256> <stopwords file> <run directory>""";

    private CorpusTool() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    public static int run(List<String> args) {
        if (args.size() == 3 && args.getFirst().equals("generate")) {
            return generate(Path.of(args.get(1)), Path.of(args.get(2)));
        }
        if (args.size() == 5 && args.getFirst().equals("check")) {
            return check(Path.of(args.get(1)), Path.of(args.get(2)), Path.of(args.get(3)), Path.of(args.get(4)));
        }
        LOGGER.error(USAGE);
        return WRONG_USAGE;
    }

    private static int generate(Path cache, Path checksumsFile) {
        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));
        write(checksumsFile, checksums.render());
        LOGGER.info("Wrote the SHA-256 of {} books to {}", checksums.bookIds().size(), checksumsFile);
        return DONE;
    }

    private static int check(Path cache, Path checksumsFile, Path stopwords, Path runDirectory) {
        CorpusCache corpus = new CorpusCache(cache);
        CorpusChecksums cached = CorpusChecksums.of(corpus);
        List<Integer> differing = cached.booksDifferingFrom(CorpusChecksums.parse(read(checksumsFile)));
        if (!differing.isEmpty()) {
            LOGGER.error("The books of {} that differ from {}: {}", cache, checksumsFile, differing);
            return CORPUS_DIFFERS;
        }
        int terms = new CorpusTerms(new Tokenizer(new FileStopwordsLoader(stopwords).stopwords())).distinct(corpus);
        Path written = CorpusSummary.of(cached, terms).writeTo(runDirectory);
        LOGGER.info("The corpus matches {}: {} books and {} distinct terms in {}", checksumsFile, cached.bookIds().size(), terms, written);
        return DONE;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
