package org.ulpgc.tarantino.indexer.adapters.index.folders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Term file names that stay distinct on case-insensitive and normalization-insensitive file systems (APFS,
 * NTFS): ASCII lowercase letters are kept and every other UTF-8 byte is written as %XX, so no two terms share
 * a name; names longer than {@value #MAX_NAME_LENGTH} characters become the SHA-256 of the term.
 */
final class TermFiles {

    private static final String SUFFIX = ".txt";
    private static final int MAX_NAME_LENGTH = 200;
    private static final String HASH_PREFIX = "#";
    private static final HexFormat UPPERCASE_HEX = HexFormat.of().withUpperCase();

    private TermFiles() {
    }

    static Path file(Path root, String term) {
        return root.resolve(encoded(firstCharacter(term))).resolve(name(term) + SUFFIX);
    }

    private static String name(String term) {
        String encoded = encoded(term);
        return encoded.length() <= MAX_NAME_LENGTH ? encoded : HASH_PREFIX + HexFormat.of().formatHex(sha256(term));
    }

    private static String encoded(String text) {
        StringBuilder name = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            if (b >= 'a' && b <= 'z') {
                name.append((char) b);
            } else {
                name.append('%').append(UPPERCASE_HEX.toHexDigits(b));
            }
        }
        return name.toString();
    }

    private static String firstCharacter(String term) {
        return term.substring(0, Character.charCount(term.codePointAt(0)));
    }

    private static byte[] sha256(String term) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(term.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
