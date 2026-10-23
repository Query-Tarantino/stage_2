package org.ulpgc.tarantino.common.http;

import io.javalin.http.Context;
import io.javalin.http.Header;

final class RetryAfter {

    private static final String SECONDS = "5";

    private RetryAfter() {
    }

    static void add(Context context) {
        context.header(Header.RETRY_AFTER, SECONDS);
    }
}
