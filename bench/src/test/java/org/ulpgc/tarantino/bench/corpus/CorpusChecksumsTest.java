package org.ulpgc.tarantino.bench.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CorpusChecksumsTest {

    private static final String SHA256_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String SHA256_OF_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir
    Path cache;

    @Test
    void hashesEachBookTextByAscendingIdLeavingOutEverythingElse() throws IOException {
        Files.writeString(cache.resolve("1342.txt"), "abc");
        Files.writeString(cache.resolve("84.txt"), "");
        Files.writeString(cache.resolve("skipped.txt"), "11\n");
        Files.writeString(cache.resolve("2701.txt.tmp"), "partial");

        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));

        assertEquals(List.of(84, 1342), checksums.bookIds());
        assertEquals("84 " + SHA256_OF_EMPTY + "\n1342 " + SHA256_OF_ABC + "\n", checksums.render());
    }

    @Test
    void hashesTheBytesOfTheTextAsTheyAre() throws IOException {
        Files.write(cache.resolve("84.txt"), new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', 'b', 'c'});
        Files.writeString(cache.resolve("1342.txt"), "abc\r\n");

        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));

        assertNotEquals(SHA256_OF_ABC, checksums.sha256ByBookId().get(84));
        assertNotEquals(SHA256_OF_ABC, checksums.sha256ByBookId().get(1342));
    }

    @Test
    void readsBackWhatItRenders() throws IOException {
        Files.writeString(cache.resolve("1342.txt"), "abc");
        Files.writeString(cache.resolve("84.txt"), "");
        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));

        assertEquals(checksums, CorpusChecksums.parse(checksums.render()));
    }

    @Test
    void namesTheBooksThatChangedOrThatOnlyOneSideHas() {
        CorpusChecksums expected = new CorpusChecksums(Map.of(84, SHA256_OF_EMPTY, 1342, SHA256_OF_ABC, 2701, SHA256_OF_ABC));
        CorpusChecksums cached = new CorpusChecksums(Map.of(84, SHA256_OF_EMPTY, 1342, SHA256_OF_EMPTY, 11, SHA256_OF_ABC));

        assertEquals(List.of(11, 1342, 2701), cached.booksDifferingFrom(expected));
        assertEquals(List.of(), expected.booksDifferingFrom(expected));
    }

    @Test
    void combinesTheChecksumsIntoTheSha256OfTheirLines() throws IOException {
        Files.writeString(cache.resolve("1342.txt"), "abc");
        CorpusChecksums checksums = CorpusChecksums.of(new CorpusCache(cache));

        assertEquals(Sha256.hex(("1342 " + SHA256_OF_ABC + "\n").getBytes(StandardCharsets.UTF_8)), checksums.combinedSha256());
    }
}
