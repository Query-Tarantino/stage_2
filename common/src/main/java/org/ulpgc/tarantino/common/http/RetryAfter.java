package org.ulpgc.tarantino.common.http;

import io.javalin.http.Context;
import io.javalin.http.Header;

/** Adds the {@code Retry-After} header that every {@code 503} carries (SPEC §6.6). */
final class RetryAfter {

    private static final String SECONDS = "5";

    private RetryAfter() {
    }

    static void add(Context context) {
        context.header(Header.RETRY_AFTER, SECONDS);
    }
}
