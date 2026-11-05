package org.ulpgc.tarantino.checks;

/** The paths, headers and JSON fields of the HTTP API (SPEC §6) that the checks use. */
final class ApiNames {

    static final String HEALTH = "/health";
    static final String SEARCH = "/search";

    static final String CONTENT_TYPE = "Content-Type";

    static final String AUTHOR = "author";
    static final String CODE = "code";
    static final String DETAIL = "detail";
    static final String LANGUAGE = "language";
    static final String LIMIT = "limit";
    static final String OFFSET = "offset";
    static final String PARAMETER = "parameter";
    static final String STATUS = "status";
    static final String TITLE = "title";
    static final String YEAR = "year";

    private ApiNames() {
    }
}
