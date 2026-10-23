package org.ulpgc.tarantino.indexer.model.terms;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Terms of SPEC §7, found by scanning code points instead of matching {@code \p{L}+}: the same letters
 * ({@link Character#isLetter(int)} is general category L), without a match object per word nor a stream.
 * The whole text is still lowercased at once, because lowercasing letter by letter differs for final sigma
 * and for letters whose lowercase form is longer.
 */
public class Tokenizer {

    private static final int MIN_TERM_LENGTH = 2;

    private final Set<String> stopwords;

    public Tokenizer(Set<String> stopwords) {
        this.stopwords = stopwords;
    }

    public TermOccurrences occurrences(int bookId, String body) {
        return new TermOccurrences(bookId, frequencies(body));
    }

    private Map<String, Integer> frequencies(String body) {
        String text = body.toLowerCase(Locale.ROOT);
        Map<String, int[]> counts = new HashMap<>();
        int index = 0;
        while (index < text.length()) {
            int start = index;
            int codePoints = 0;
            while (index < text.length() && Character.isLetter(text.codePointAt(index))) {
                index += Character.charCount(text.codePointAt(index));
                codePoints++;
            }
            if (codePoints >= MIN_TERM_LENGTH) {
                count(text.substring(start, index), counts);
            }
            if (codePoints == 0) {
                index += Character.charCount(text.codePointAt(index));
            }
        }
        return frequenciesOf(counts);
    }

    private void count(String term, Map<String, int[]> counts) {
        if (!stopwords.contains(term)) {
            counts.computeIfAbsent(term, key -> new int[1])[0]++;
        }
    }

    private static Map<String, Integer> frequenciesOf(Map<String, int[]> counts) {
        Map<String, Integer> frequencies = HashMap.newHashMap(counts.size());
        counts.forEach((term, count) -> frequencies.put(term, count[0]));
        return frequencies;
    }
}
