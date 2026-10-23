package org.ulpgc.tarantino.common.mongo;

/** Every field of the collections of SPEC §4.3. */
public final class MongoFields {

    public static final String ID = "_id";
    public static final String STATUS = "status";
    public static final String ATTEMPTS = "attempts";
    public static final String CLAIM_ID = "claim_id";
    public static final String LEASE_UNTIL = "lease_until";
    public static final String BATCH_ID = "batch_id";
    public static final String GENERATION = "generation";
    public static final String LAST_ERROR = "last_error";
    public static final String ENQUEUED_AT = "enqueued_at";
    public static final String DOWNLOADED_AT = "downloaded_at";
    public static final String PUBLISHED_AT = "published_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String TITLE = "title";
    public static final String AUTHOR = "author";
    public static final String AUTHOR_TERMS = "author_terms";
    public static final String LANGUAGES = "languages";
    public static final String LANGUAGE_NAMES = "language_names";
    public static final String YEAR = "year";
    public static final String ORIGINAL_YEAR = "original_year";
    public static final String TERM = "term";
    public static final String BOOK_IDS = "book_ids";
    public static final String TFS = "tfs";
    public static final String OWNER = "owner";
    public static final String OPENED_AT = "opened_at";
    public static final String ABANDONED_AT = "abandoned_at";
    public static final String SWITCHED_AT = "switched_at";
    public static final String TARGET_GENERATION = "target_generation";
    public static final String REQUESTED_AT = "requested_at";

    private MongoFields() {
    }
}
