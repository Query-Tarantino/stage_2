package org.ulpgc.tarantino.bench.corpus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusToolTest {

    @TempDir
    Path cache;

    @TempDir
    Path workload;

    @TempDir
    Path runDirectory;

    private Path checksums;
    private Path stopwords;

    @BeforeEach
    void fillTheCache() throws IOException {
        Files.writeString(cache.resolve("84.txt"), "Frankenstein, the modern Prometheus");
        Files.writeString(cache.resolve("1342.txt"), "Pride and prejudice");
        checksums = workload.resolve("corpus.sha256");
        stopwords = Files.writeString(workload.resolve("stopwords.txt"), "the\nand\n");
    }

    @Test
    void writesCorpusJsonWhenTheCacheMatchesWhatItGenerated() throws IOException {
        assertEquals(CorpusTool.DONE, CorpusTool.run(List.of("generate", cache.toString(), checksums.toString())));

        assertEquals(CorpusTool.DONE, check());
        assertEquals(CorpusSummary.of(CorpusChecksums.of(new CorpusCache(cache)), 5),
                TarantinoJson.read(Files.readString(runDirectory.resolve(CorpusSummary.FILE_NAME)), CorpusSummary.class));
    }

    @Test
    void stopsWithoutWritingCorpusJsonWhenATextChanged() throws IOException {
        CorpusTool.run(List.of("generate", cache.toString(), checksums.toString()));
        Files.writeString(cache.resolve("1342.txt"), "Pride and prejudice, updated by Gutenberg");

        assertEquals(CorpusTool.CORPUS_DIFFERS, check());
        assertFalse(Files.exists(runDirectory.resolve(CorpusSummary.FILE_NAME)));
    }

    @Test
    void stopsWhenABookIsMissingFromTheCache() throws IOException {
        CorpusTool.run(List.of("generate", cache.toString(), checksums.toString()));
        Files.delete(cache.resolve("84.txt"));

        assertEquals(CorpusTool.CORPUS_DIFFERS, check());
    }

    @Test
    void answersWrongUsageToAnythingElse() {
        assertEquals(CorpusTool.WRONG_USAGE, CorpusTool.run(List.of()));
        assertEquals(CorpusTool.WRONG_USAGE, CorpusTool.run(List.of("check", cache.toString())));
        assertTrue(Files.notExists(checksums));
    }

    private int check() {
        return CorpusTool.run(List.of("check", cache.toString(), checksums.toString(), stopwords.toString(), runDirectory.toString()));
    }
}
