package org.ulpgc.tarantino.common.mongo;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The collections of SPEC §4.3: those of a generation carry its suffix, {@code _g<n>}. */
public final class MongoCollections {

    public static final String BOOK_STATES = "book_states";
    public static final String INDEX_STATE = "index_state";
    public static final String CURRENT = "current";
    public static final String REBUILD = "rebuild";

    private static final Pattern GENERATION_COLLECTION = Pattern.compile("(metadata|postings|batches)_g([1-9][0-9]*)");

    private MongoCollections() {
    }

    public static String metadata(int generation) {
        return "metadata_g" + generation;
    }

    public static String postings(int generation) {
        return "postings_g" + generation;
    }

    public static String batches(int generation) {
        return "batches_g" + generation;
    }

    /** The generation of a generation's collection, so {@code control} can find the old ones; empty for any other. */
    public static OptionalInt generationOf(String collection) {
        Matcher matcher = GENERATION_COLLECTION.matcher(collection);
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(2))) : OptionalInt.empty();
    }
}
