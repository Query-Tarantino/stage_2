package org.ulpgc.tarantino.common.terms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tokenizer scans code points by hand; it must count exactly what the regular expression {@code \p{L}+} finds. */
class TokenizerEquivalenceTest {

    private static final Pattern TERM = Pattern.compile("\\p{L}+");
    private static final Set<String> STOPWORDS = Set.of("the", "of", "σας");
    private static final List<String> LATIN_WORDS = List.of("the", "The", "OF", "island", "ISLAND", "x", "a", "ab", "Cæsar");
    private static final List<String> COMBINING_MARKS_AND_PUNCTUATION = List.of("café", "cafe\u0301", "don't", "e-mail",
            "rock-and-roll", "—", "...", "_");
    private static final List<String> DIGITS = List.of("1984year", "123");
    private static final List<String> LOWER_CASE_BY_CONTEXT_OR_OF_ANOTHER_LENGTH = List.of("ΟΔΟΣ", "λόγος", "ΣΑΣ", "İstanbul",
            "Straße", "STRAẞE", "ſhape", "Ǆemal", "ǅ");
    private static final List<String> OTHER_SCRIPTS = List.of("漢字", "한국어", "مرحبا");
    private static final List<String> SUPPLEMENTARY_LETTERS = List.of("𝒜", "𝒜𝒜");
    private static final List<String> WHITESPACE = List.of("  ", "\n", "\u00a0");
    private static final List<String> PIECES = Stream.of(LATIN_WORDS, COMBINING_MARKS_AND_PUNCTUATION, DIGITS,
            LOWER_CASE_BY_CONTEXT_OR_OF_ANOTHER_LENGTH, OTHER_SCRIPTS, SUPPLEMENTARY_LETTERS, WHITESPACE).flatMap(List::stream).toList();

    private final Tokenizer tokenizer = new Tokenizer(STOPWORDS);

    @Test
    void countsTheSameTermsAsTheSpecRegularExpression() {
        Random random = new Random(20261001);
        for (int text = 0; text < 5000; text++) {
            StringBuilder body = new StringBuilder();
            for (int piece = random.nextInt(12); piece >= 0; piece--) {
                body.append(PIECES.get(random.nextInt(PIECES.size()))).append(random.nextBoolean() ? " " : "");
            }
            assertEquals(reference(body.toString()), tokenizer.frequencies(body.toString()), body::toString);
        }
    }

    private static Map<String, Integer> reference(String body) {
        return TERM.matcher(body.toLowerCase(Locale.ROOT)).results()
                .map(MatchResult::group)
                .filter(term -> term.codePointCount(0, term.length()) >= 2 && !STOPWORDS.contains(term))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(term -> 1)));
    }
}
