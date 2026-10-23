package org.ulpgc.tarantino.common.mongo;

import org.bson.Document;

import java.time.Duration;
import java.util.List;

/** Pieces of the update pipelines of SPEC §5.1, which compare dates with the server's time, {@code $$NOW}. */
public final class UpdatePipelines {

    public static final String NOW = "$$NOW";

    private UpdatePipelines() {
    }

    /** {@code {$ifNull: ["$field", value]}}: keeps what an existing document has, so an upsert never overwrites it. */
    public static Document ifNull(String field, Object value) {
        return new Document("$ifNull", List.of("$" + field, value));
    }

    /** {@code {$add: ["$$NOW", millis]}}: the server's time plus a lease. */
    public static Document nowPlus(Duration duration) {
        return new Document("$add", List.of(NOW, duration.toMillis()));
    }

    public static List<Document> set(Document fields) {
        return List.of(new Document("$set", fields));
    }

    public static List<Document> setThenUnset(Document fields, String... unset) {
        return List.of(new Document("$set", fields), new Document("$unset", List.of(unset)));
    }
}
