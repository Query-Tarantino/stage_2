package org.ulpgc.tarantino.common.mongo;

import org.bson.Document;

import java.util.List;

final class UpdatePipelines {

    static final String NOW = "$$NOW";

    private UpdatePipelines() {
    }

    /** {@code {$ifNull: ["$field", value]}}: keeps what an existing document has, so an upsert never overwrites it. */
    static Document ifNull(String field, Object value) {
        return new Document("$ifNull", List.of("$" + field, value));
    }

    static List<Document> set(Document fields) {
        return List.of(new Document("$set", fields));
    }
}
