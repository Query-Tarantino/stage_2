package org.ulpgc.tarantino.common.terms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer(Set.of("the", "of"));

    @Test
    void countsLowercasedTermsWithoutStopwordsOrShortTokens() {
        assertEquals(Map.of("island", 2, "shipwreck", 1), tokenizer.frequencies("The Island of the island, a SHIPWRECK!"));
    }

    @Test
    void keepsAccentedLettersAndSplitsOnDigitsAndApostrophes() {
        assertEquals(Map.of("café", 1, "don", 1, "year", 1), tokenizer.frequencies("Café don't 1984year"));
    }

    @Test
    void measuresTermLengthInCodePoints() {
        assertEquals(Map.of("𝒜𝒜", 1), tokenizer.frequencies("𝒜 𝒜𝒜"));
    }

    @Test
    void listsDistinctTermsInOrderOfFirstAppearance() {
        assertEquals(List.of("island", "shipwreck"), tokenizer.distinctTerms("The ISLAND of a Shipwreck island"));
    }
}
