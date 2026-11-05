package org.ulpgc.tarantino.bench.corpus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-256 of a text in lower-case hex, as {@code workload/corpus.sha256} lists each book (SPEC §9). */
final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private Sha256() {
    }

    static String hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITHM).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
