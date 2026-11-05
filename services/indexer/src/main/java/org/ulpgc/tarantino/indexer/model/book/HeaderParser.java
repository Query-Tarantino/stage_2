package org.ulpgc.tarantino.indexer.model.book;

import org.ulpgc.tarantino.common.language.LanguageCodes;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * The {@code metadata_g<n>} fields of a header (SPEC §4.3). Each field is the first line that starts with its name;
 * {@code year} is the year of {@code Release date:}, read before {@code [eBook #…]}, whose number can also have four
 * digits.
 */
public class HeaderParser {

    private static final Pattern TITLE = fieldPattern("Title");
    private static final Pattern AUTHOR = fieldPattern("Author");
    private static final Pattern LANGUAGE = fieldPattern("Language");
    private static final Pattern RELEASE_DATE = fieldPattern("Release date");
    private static final Pattern ORIGINAL_PUBLICATION = fieldPattern("Original publication");
    private static final Pattern YEAR = Pattern.compile("\\b([0-9]{4})\\b");
    private static final String LANGUAGE_SEPARATOR = ",";
    private static final String EBOOK_NUMBER = "[";

    private final Tokenizer tokenizer;

    public HeaderParser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public BookMetadata metadata(BookText text) {
        String header = text.header();
        Optional<String> author = field(AUTHOR, header);
        List<String> languageNames = field(LANGUAGE, header).map(HeaderParser::languageNames).orElse(List.of());
        return new BookMetadata(text.bookId(),
                field(TITLE, header).orElse(null),
                author.orElse(null),
                author.map(tokenizer::distinctTerms).orElse(List.of()),
                languageNames.stream().flatMap(name -> LanguageCodes.code(name).stream()).toList(),
                languageNames,
                field(RELEASE_DATE, header).map(HeaderParser::beforeEbookNumber).flatMap(HeaderParser::lastYear).orElse(null),
                field(ORIGINAL_PUBLICATION, header).flatMap(HeaderParser::lastYear).orElse(null));
    }

    private static Pattern fieldPattern(String name) {
        return Pattern.compile("^" + name + ":[ \\t]*(.+)$", Pattern.MULTILINE | Pattern.UNIX_LINES);
    }

    private static Optional<String> field(Pattern pattern, String header) {
        return pattern.matcher(header).results().findFirst().map(match -> match.group(1).strip());
    }

    private static List<String> languageNames(String field) {
        return Arrays.stream(field.split(LANGUAGE_SEPARATOR)).map(String::strip).filter(name -> !name.isEmpty()).toList();
    }

    private static String beforeEbookNumber(String releaseDate) {
        int ebookNumber = releaseDate.indexOf(EBOOK_NUMBER);
        return ebookNumber < 0 ? releaseDate : releaseDate.substring(0, ebookNumber);
    }

    private static Optional<Integer> lastYear(String text) {
        return YEAR.matcher(text).results().map(MatchResult::group).reduce((earlier, later) -> later).map(Integer::valueOf);
    }
}
