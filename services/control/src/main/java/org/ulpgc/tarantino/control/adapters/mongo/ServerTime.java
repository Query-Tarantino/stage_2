package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;

/** Filters that compare a date with the Mongo server's time, {@code $$NOW}, as every expiration of SPEC §5.1 does. */
final class ServerTime {

    private ServerTime() {
    }

    /** {@code {$expr: {$lte: ["$field", "$$NOW"]}}}: the date in the field has already passed. */
    static Bson passed(String field) {
        return Filters.expr(new Document("$lte", List.of("$" + field, NOW)));
    }
}
