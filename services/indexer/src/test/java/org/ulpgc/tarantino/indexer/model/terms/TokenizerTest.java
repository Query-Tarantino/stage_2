package org.ulpgc.tarantino.indexer.model.terms;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer(Set.of("the", "of"));

    @Test
    void countsLowercasedTermsWithoutStopwordsOrShortTokens() {
        TermOccurrences occurrences = tokenizer.occurrences(5, "The Island of the island, a SHIPWRECK!");

        assertEquals(5, occurrences.bookId());
        assertEquals(Map.of("island", 2, "shipwreck", 1), occurrences.frequencies());
    }

    @Test
    void keepsAccentedLettersAndSplitsOnDigitsAndApostrophes() {
        TermOccurrences occurrences = tokenizer.occurrences(5, "Café don't 1984year");

        assertEquals(Map.of("café", 1, "don", 1, "year", 1), occurrences.frequencies());
    }

    @Test
    void measuresTermLengthInCodePoints() {
        TermOccurrences occurrences = tokenizer.occurrences(5, "\uD835\uDC9C \uD835\uDC9C\uD835\uDC9C");

        assertEquals(Map.of("\uD835\uDC9C\uD835\uDC9C", 1), occurrences.frequencies());
    }
}
