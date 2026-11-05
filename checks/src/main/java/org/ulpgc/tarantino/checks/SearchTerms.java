package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGES;
import static org.ulpgc.tarantino.checks.ApiNames.TITLE;

/** A word of a sample book that {@code GET /search} finds it by: from its title or else its author, tokenized as SPEC §6.3 says. */
final class SearchTerms {

    private static final int SHORTEST_DESCRIPTIVE_TERM = 3;
    private static final String ANY_BOOK_TERM = "pride";

    private final Tokenizer tokenizer;

    SearchTerms(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    /** The first sample book in {@code language}, matched case-insensitively against its {@code languages}. */
    static Optional<JsonNode> bookIn(List<JsonNode> books, String language) {
        return books.stream()
                .filter(book -> book.has(LANGUAGES) && book.get(LANGUAGES).isArray())
                .filter(book -> StreamSupport.stream(book.get(LANGUAGES).spliterator(), false)
                        .anyMatch(code -> language.equalsIgnoreCase(code.asText())))
                .findFirst();
    }

    String termOf(JsonNode book) {
        return Stream.of(TITLE, AUTHOR)
                .filter(book::hasNonNull)
                .map(field -> tokenizer.distinctTerms(book.get(field).asText()))
                .filter(terms -> !terms.isEmpty())
                .map(SearchTerms::mostDescriptive)
                .findFirst()
                .orElse(ANY_BOOK_TERM);
    }

    /** The term of a sample book in {@code language}, or {@code otherwise} when there is none. */
    String termIn(List<JsonNode> books, String language, String otherwise) {
        return bookIn(books, language).map(this::termOf).orElse(otherwise);
    }

    private static String mostDescriptive(List<String> terms) {
        return terms.stream().filter(term -> term.length() >= SHORTEST_DESCRIPTIVE_TERM).findFirst().orElse(terms.getFirst());
    }
}
