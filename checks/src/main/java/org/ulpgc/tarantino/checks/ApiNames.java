package org.ulpgc.tarantino.checks;

/** The paths, headers and JSON fields of the HTTP API (SPEC §6) that the checks use. */
final class ApiNames {

    static final String HEALTH = "/health";
    static final String INGEST = "/ingest/";
    static final String INGEST_STATUS = "/ingest/status/";
    static final String INGEST_LIST = "/ingest/list";
    static final String INDEX_UPDATE = "/index/update/";
    static final String INDEX_STATUS = "/index/status";
    static final String CONTROL_STATUS = "/control/status";
    static final String BOOKS_PATH = "/books/";
    static final String SEARCH = "/search";

    static final String CONTENT_TYPE = "Content-Type";
    static final String LOCATION = "Location";

    static final String AUTHOR = "author";
    static final String BOOK_ID = "book_id";
    static final String BOOKS = "books";
    static final String BOOKS_INDEXED = "books_indexed";
    static final String CODE = "code";
    static final String COUNT = "count";
    static final String DETAIL = "detail";
    static final String GENERATION = "generation";
    static final String LANGUAGE = "language";
    static final String LANGUAGES = "languages";
    static final String LIMIT = "limit";
    static final String OFFSET = "offset";
    static final String PARAMETER = "parameter";
    static final String QUEUES = "queues";
    static final String STATUS = "status";
    static final String TITLE = "title";
    static final String YEAR = "year";

    private ApiNames() {
    }
}
