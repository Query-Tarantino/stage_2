package org.ulpgc.tarantino.indexer.model.book;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderParserTest {

    private final HeaderParser parser = new HeaderParser();
    private final Path body = Path.of("datalake/20250925/14/5.body.txt");

    @Test
    void extractsTitleAuthorAndLanguage() {
        String header = """
                The Project Gutenberg eBook of Robinson Crusoe

                Title: Robinson Crusoe
                Author: Daniel Defoe
                Release date: October 1, 1996 [eBook #521]
                Language: English
                """;

        Book book = parser.book(new BookText(5, header, "", body));

        assertEquals(new Book(5, "Robinson Crusoe", "Daniel Defoe", "English", body), book);
    }

    @Test
    void leavesMissingFieldsAsNull() {
        Book book = parser.book(new BookText(5, "Title: Only a title", "", body));

        assertEquals("Only a title", book.title());
        assertNull(book.author());
        assertNull(book.language());
    }
}
