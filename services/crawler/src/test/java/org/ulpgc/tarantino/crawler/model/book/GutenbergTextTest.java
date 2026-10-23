package org.ulpgc.tarantino.crawler.model.book;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GutenbergTextTest {

    private static final String RAW = """
            Title: Pride and Prejudice\r
            Author: Jane Austen\r
            \r
            *** START OF THE PROJECT GUTENBERG EBOOK PRIDE AND PREJUDICE ***\r
            \r
            It is a truth universally acknowledged.\r
            \r
            *** END OF THE PROJECT GUTENBERG EBOOK PRIDE AND PREJUDICE ***\r
            License text.\r
            """;

    @Test
    void splitsHeaderAndBodyDiscardingMarkersAndFooter() {
        BookText book = GutenbergText.bookText(1342, RAW);

        assertEquals(1342, book.bookId());
        assertEquals("Title: Pride and Prejudice\nAuthor: Jane Austen", book.header());
        assertEquals("It is a truth universally acknowledged.", book.body());
    }

    @Test
    void acceptsLegacyThisMarkers() {
        String raw = "Header\n*** START OF THIS PROJECT GUTENBERG EBOOK X ***\nBody\n*** END OF THIS PROJECT GUTENBERG EBOOK X ***";

        assertEquals("Body", GutenbergText.bookText(1, raw).body());
    }

    @Test
    void failsWhenMarkersAreMissing() {
        DownloadException error = assertThrows(DownloadException.class, () -> GutenbergText.bookText(1, "no markers"));

        assertEquals(FailureReason.MISSING_MARKERS, error.reason());
    }
}
