package org.ulpgc.tarantino.checks;

/** The paths, headers and JSON fields of the HTTP API (SPEC §6) that the checks use. */
final class ApiNames {

    static final String HEALTH = "/health";

    static final String CONTENT_TYPE = "Content-Type";

    static final String CODE = "code";
    static final String DETAIL = "detail";
    static final String STATUS = "status";
    static final String TITLE = "title";

    private ApiNames() {
    }
}
