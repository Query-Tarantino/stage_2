package org.ulpgc.tarantino.indexer.model.terms;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tokenizer scans code points by hand; it must count exactly what the regular expression of SPEC §7 finds. */
class TokenizerEquivalenceTest {

    private static final Pattern TERM = Pattern.compile("\\p{L}+");
    private static final Set<String> STOPWORDS = Set.of("the", "of", "σας");
    // Letters, digits, punctuation, combining marks, supplementary letters and characters whose lowercase
    // form depends on context (final sigma) or changes length (dotted capital I)
    private static final String[] PIECES = {
            "the", "The", "OF", "island", "ISLAND", "café", "café", "don't", "1984year", "x", "a", "ab",
            "ΟΔΟΣ", "λόγος", "ΣΑΣ", "İstanbul", "Straße", "STRAẞE", "ſhape", "Cæsar", "漢字", "한국어", "مرحبا",
            "𝒜", "𝒜𝒜", "e-mail", "rock-and-roll", "  ", "\n", "—", "...", "123", "_",
            " ", "Ǆemal", "ǅ"};

    private final Tokenizer tokenizer = new Tokenizer(STOPWORDS);

    @Test
    void countsTheSameTermsAsTheSpecRegularExpression() {
        Random random = new Random(20261001);
        for (int text = 0; text < 5000; text++) {
            StringBuilder body = new StringBuilder();
            for (int piece = random.nextInt(12); piece >= 0; piece--) {
                body.append(PIECES[random.nextInt(PIECES.length)]).append(random.nextBoolean() ? " " : "");
            }
            assertEquals(reference(body.toString()), tokenizer.occurrences(1, body.toString()).frequencies(), body::toString);
        }
    }

    private static Map<String, Integer> reference(String body) {
        return TERM.matcher(body.toLowerCase(Locale.ROOT)).results()
                .map(MatchResult::group)
                .filter(term -> term.codePointCount(0, term.length()) >= 2 && !STOPWORDS.contains(term))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(term -> 1)));
    }
}
