package org.ulpgc.tarantino.indexer.model.terms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchPostingsTest {

    @Test
    void writesOnePostingPerTermWithAscendingBooksAndTheirFrequencies() {
        Map<Integer, Map<String, Integer>> frequencies = Map.of(
                1342, Map.of("pride", 55, "prejudice", 11),
                84, Map.of("pride", 6, "monster", 30),
                345, Map.of("prejudice", 2));

        assertEquals(List.of(
                new TermPostings("monster", List.of(84), List.of(30)),
                new TermPostings("prejudice", List.of(345, 1342), List.of(2, 11)),
                new TermPostings("pride", List.of(84, 1342), List.of(6, 55))), BatchPostings.of(frequencies));
    }

    @Test
    void hasNoPostingsForABatchWithoutTerms() {
        assertEquals(List.of(), BatchPostings.of(Map.of(1342, Map.of())));
    }
}
