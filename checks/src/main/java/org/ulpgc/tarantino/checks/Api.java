package org.ulpgc.tarantino.checks;

import java.io.IOException;

/**
 * The stack under check through its two nginx entry ports (SPEC §6.1), with every response validated against
 * {@code api/openapi.yaml} by the {@link CheckedClient}.
 */
record Api(CheckedClient client, String frontEndUrl, String operationalUrl) {

    private static final String NO_BODY = "";
    private static final String TRAILING_SLASH = "/";

    static Api of(CheckedClient client, String frontEndUrl, String operationalUrl) {
        return new Api(client, withoutTrailingSlash(frontEndUrl), withoutTrailingSlash(operationalUrl));
    }

    CheckedClient.Response frontEnd(String path) throws IOException, InterruptedException {
        return client.get(frontEndUrl + path);
    }

    CheckedClient.Response operational(String path) throws IOException, InterruptedException {
        return client.get(operationalUrl + path);
    }

    CheckedClient.Response postOperational(String path) throws IOException, InterruptedException {
        return client.post(operationalUrl + path, NO_BODY);
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith(TRAILING_SLASH) ? url.substring(0, url.length() - TRAILING_SLASH.length()) : url;
    }
}
