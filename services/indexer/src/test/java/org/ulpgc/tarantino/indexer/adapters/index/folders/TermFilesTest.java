package org.ulpgc.tarantino.indexer.adapters.index.folders;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TermFilesTest {

    private static final Path ROOT = Path.of("index");

    @Test
    void keepsAsciiLowercaseTermsReadableUnderTheirFirstLetter() {
        assertEquals(ROOT.resolve("w/whale.txt"), TermFiles.file(ROOT, "whale"));
    }

    @Test
    void encodesEveryOtherUtf8ByteAndUsesTheFirstCodePointAsDirectory() {
        assertEquals(ROOT.resolve("%C3%A9/%C3%A9cume.txt"), TermFiles.file(ROOT, "écume"));
        assertEquals(ROOT.resolve("%F0%9D%92%9C/%F0%9D%92%9Cb.txt"), TermFiles.file(ROOT, "𝒜b"));
    }

    @Test
    void namesTermsThatOnlyDifferInCaseFoldingOrNormalizationDifferently() {
        assertNotEquals(TermFiles.file(ROOT, "shape"), TermFiles.file(ROOT, "ſhape"));
        assertNotEquals(TermFiles.file(ROOT, Normalizer.normalize("café", Normalizer.Form.NFC)),
                TermFiles.file(ROOT, Normalizer.normalize("café", Normalizer.Form.NFD)));
    }

    @Test
    void hashesNamesLongerThan200Characters() {
        String longTerm = "漢".repeat(100);

        assertEquals(ROOT.resolve("%E6%BC%A2/#" + sha256Hex(longTerm) + ".txt"), TermFiles.file(ROOT, longTerm));
        assertEquals(ROOT.resolve("l/" + "l".repeat(200) + ".txt"), TermFiles.file(ROOT, "l".repeat(200)));
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
