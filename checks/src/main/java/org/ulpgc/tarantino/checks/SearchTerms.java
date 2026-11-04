package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** A term that finds a sample book, with the same tokenizer as {@code query} (SPEC §7). */
final class SearchTerms {

    private static final String STOPWORDS = "workload/stopwords.txt";
    private static final List<String> FIELDS_WITH_TERMS = List.of("title", "author");
    private static final String LANGUAGES = "languages";
    private static final String FALLBACK = "pride";
    private static final int SHORT_WORD = 2;

    private final Tokenizer tokenizer;

    private SearchTerms(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    /** Without {@code workload/stopwords.txt}, every word counts. */
    static SearchTerms fromRepository() {
        Set<String> stopwords = RepositoryFiles.find(STOPWORDS).map(path -> new FileStopwordsLoader(path).stopwords()).orElse(Set.of());
        return new SearchTerms(new Tokenizer(stopwords));
    }

    /** The first sample book in that language, compared without case. */
    static Optional<JsonNode> firstIn(List<JsonNode> books, String language) {
        return books.stream()
                .filter(book -> book.has(LANGUAGES) && StreamSupport.stream(book.get(LANGUAGES).spliterator(), false)
                        .anyMatch(code -> language.equalsIgnoreCase(code.asText())))
                .findFirst();
    }

    /**
     * From the title, or from the author when the title has no words: its first word longer than two letters, or its first
     * word when none is.
     */
    String termFor(JsonNode book) {
        return FIELDS_WITH_TERMS.stream()
                .filter(field -> book.has(field) && !book.get(field).isNull())
                .map(field -> tokenizer.distinctTerms(book.get(field).asText()))
                .filter(terms -> !terms.isEmpty())
                .map(SearchTerms::firstDescriptive)
                .findFirst()
                .orElse(FALLBACK);
    }

    private static String firstDescriptive(List<String> terms) {
        return Stream.concat(terms.stream().filter(term -> term.length() > SHORT_WORD), terms.stream()).findFirst().orElseThrow();
    }
}
