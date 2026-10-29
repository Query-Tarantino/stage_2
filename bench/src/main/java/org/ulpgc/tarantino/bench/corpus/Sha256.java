package org.ulpgc.tarantino.bench.corpus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
