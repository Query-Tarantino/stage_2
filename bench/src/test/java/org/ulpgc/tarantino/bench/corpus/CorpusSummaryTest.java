package org.ulpgc.tarantino.bench.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorpusSummaryTest {

    @TempDir
    Path cache;

    @TempDir
    Path runDirectory;

    @Test
    void countsTheDistinctTermsOfTheWholeCorpusWithoutStopwords() throws IOException {
        Files.writeString(cache.resolve("84.txt"), "The monster and the creator");
        Files.writeString(cache.resolve("1342.txt"), "Pride and prejudice, the MONSTER of a novel");

        int terms = new CorpusTerms(new Tokenizer(Set.of("the", "and", "of"))).distinct(new CorpusCache(cache));

        assertEquals(5, terms);
    }

    @Test
    void writesTheIdsTheirCombinedSha256AndHowManyBooksAndTermsInSnakeCase() throws IOException {
        Files.writeString(cache.resolve("1342.txt"), "abc");
        Files.writeString(cache.resolve("84.txt"), "");
        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));

        Path written = CorpusSummary.of(checksums, 7).writeTo(runDirectory);

        JsonNode corpus = TarantinoJson.mapper().readTree(Files.readString(written));
        assertEquals(runDirectory.resolve("corpus.json"), written);
        assertEquals("[84,1342]", corpus.get("ids").toString());
        assertEquals(checksums.combinedSha256(), corpus.get("combined_sha256").asText());
        assertEquals(2, corpus.get("books").asInt());
        assertEquals(7, corpus.get("terms").asInt());
    }
}
