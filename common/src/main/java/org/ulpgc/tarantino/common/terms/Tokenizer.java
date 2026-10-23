package org.ulpgc.tarantino.common.terms;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The terms shared by indexing and search (SPEC §6.3): runs of at least two letters, lowercased with
 * {@link Locale#ROOT} and without stopwords. They are found by scanning code points instead of matching
 * {@code \p{L}+}: the same letters ({@link Character#isLetter(int)} is general category L), without a match object
 * per word nor a stream. The whole text is still lowercased at once, because lowercasing letter by letter differs
 * for final sigma and for letters whose lowercase form is longer.
 */
public class Tokenizer {

    private static final int MIN_TERM_LENGTH = 2;

    private final Set<String> stopwords;

    public Tokenizer(Set<String> stopwords) {
        this.stopwords = stopwords;
    }

    /** How many times each term appears in the text, for the indexer. */
    public Map<String, Integer> frequencies(String text) {
        Map<String, int[]> counts = new HashMap<>();
        forEachTerm(text, term -> counts.computeIfAbsent(term, key -> new int[1])[0]++);
        return frequenciesOf(counts);
    }

    /** The terms without repeats in order of first appearance, for {@code q}, {@code author} and {@code author_terms}. */
    public List<String> distinctTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        forEachTerm(text, terms::add);
        return List.copyOf(terms);
    }

    private void forEachTerm(String original, Consumer<String> action) {
        String text = original.toLowerCase(Locale.ROOT);
        int index = 0;
        while (index < text.length()) {
            int start = index;
            int codePoints = 0;
            while (index < text.length() && Character.isLetter(text.codePointAt(index))) {
                index += Character.charCount(text.codePointAt(index));
                codePoints++;
            }
            if (codePoints >= MIN_TERM_LENGTH) {
                accept(text.substring(start, index), action);
            }
            if (codePoints == 0) {
                index += Character.charCount(text.codePointAt(index));
            }
        }
    }

    private void accept(String term, Consumer<String> action) {
        if (!stopwords.contains(term)) {
            action.accept(term);
        }
    }

    private static Map<String, Integer> frequenciesOf(Map<String, int[]> counts) {
        Map<String, Integer> frequencies = HashMap.newHashMap(counts.size());
        counts.forEach((term, count) -> frequencies.put(term, count[0]));
        return frequencies;
    }
}
