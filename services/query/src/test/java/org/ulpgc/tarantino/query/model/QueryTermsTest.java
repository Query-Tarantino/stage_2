package org.ulpgc.tarantino.query.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryTermsTest {

    @Test
    void normalizesLikeTheIndexerAndRemovesDuplicates() {
        Set<String> terms = QueryTerms.of("The ISLAND of a Shipwreck island", Set.of("the", "of"));

        assertEquals(List.of("island", "shipwreck"), List.copyOf(terms));
    }
}
