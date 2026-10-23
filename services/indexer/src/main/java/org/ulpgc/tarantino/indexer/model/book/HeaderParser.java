package org.ulpgc.tarantino.indexer.model.book;

import java.util.regex.Pattern;

public class HeaderParser {

    private static final Pattern TITLE = fieldPattern("Title");
    private static final Pattern AUTHOR = fieldPattern("Author");
    private static final Pattern LANGUAGE = fieldPattern("Language");

    public Book book(BookText text) {
        return new Book(text.bookId(), field(TITLE, text), field(AUTHOR, text), field(LANGUAGE, text), text.bodyPath());
    }

    private static Pattern fieldPattern(String name) {
        // Only \n ends a line (SPEC §7), as in Python and C++: by default Java also ends lines at \r, U+0085 and U+2028
        return Pattern.compile("^" + name + ":[ \\t]*(.+)$", Pattern.MULTILINE | Pattern.UNIX_LINES);
    }

    private static String field(Pattern pattern, BookText text) {
        return pattern.matcher(text.header()).results()
                .findFirst()
                .map(match -> match.group(1).strip())
                .orElse(null);
    }
}
