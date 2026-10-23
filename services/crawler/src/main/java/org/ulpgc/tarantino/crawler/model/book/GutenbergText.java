package org.ulpgc.tarantino.crawler.model.book;

import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class GutenbergText {

    private static final Pattern START_MARKER =
            Pattern.compile("\\*\\*\\* ?START OF (THE|THIS) PROJECT GUTENBERG EBOOK[^\\n]*\\n");
    private static final Pattern END_MARKER =
            Pattern.compile("\\*\\*\\* ?END OF (THE|THIS) PROJECT GUTENBERG EBOOK");

    private GutenbergText() {
    }

    public static BookText bookText(int bookId, String rawText) {
        String text = rawText.replace("\r\n", "\n");
        MatchResult start = marker(START_MARKER, text, 0, bookId);
        MatchResult end = marker(END_MARKER, text, start.end(), bookId);
        return new BookText(bookId, text.substring(0, start.start()).strip(), text.substring(start.end(), end.start()).strip());
    }

    private static MatchResult marker(Pattern marker, String text, int from, int bookId) {
        return marker.matcher(text).region(from, text.length()).results()
                .findFirst()
                .orElseThrow(() -> new DownloadException(FailureReason.MISSING_MARKERS, "Missing Gutenberg markers in book " + bookId));
    }
}
