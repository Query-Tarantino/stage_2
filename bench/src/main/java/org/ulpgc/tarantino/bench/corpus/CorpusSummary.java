package org.ulpgc.tarantino.bench.corpus;

import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The {@code corpus.json} of a run (SPEC §9): the corpus ids in order, their combined SHA-256 and how many books and distinct terms it has. */
public record CorpusSummary(List<Integer> ids, String combinedSha256, int books, int terms) {

    public static final String FILE_NAME = "corpus.json";

    public static CorpusSummary of(CorpusChecksums checksums, int distinctTerms) {
        List<Integer> ids = checksums.bookIds();
        return new CorpusSummary(ids, checksums.combinedSha256(), ids.size(), distinctTerms);
    }

    public Path writeTo(Path runDirectory) {
        try {
            return Files.writeString(runDirectory.resolve(FILE_NAME), TarantinoJson.write(this));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
