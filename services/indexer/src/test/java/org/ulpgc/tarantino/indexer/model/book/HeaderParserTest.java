package org.ulpgc.tarantino.indexer.model.book;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderParserTest {

    private final HeaderParser parser = new HeaderParser(new Tokenizer(Set.of("the", "and")));

    @Test
    void readsEveryMetadataFieldOfARealHeader() {
        String header = """
                The Project Gutenberg eBook of Pride and Prejudice

                Title: Pride and Prejudice

                Author: Jane Austen

                Release date: June 1, 1998 [eBook #1342]
                                Most recently updated: September 1, 2026

                Language: English
                """;

        assertEquals(new BookMetadata(1342, "Pride and Prejudice", "Jane Austen", List.of("jane", "austen"), List.of("en"),
                List.of("English"), 1998, null), parser.metadata(new BookText(1342, header, "")));
    }

    @Test
    void takesTheReleaseYearAndNeverTheEbookNumber() {
        BookMetadata metadata = parser.metadata(new BookText(2000, "Release date: December 1, 1999 [eBook #2000]", ""));

        assertEquals(1999, metadata.year());
    }

    @Test
    void readsTheOriginalPublicationYearWhenTheHeaderHasIt() {
        String header = "Release date: May 1, 2001 [eBook #2650]\nOriginal publication: Toronto: s.n., 1920";

        assertEquals(1920, parser.metadata(new BookText(2650, header, "")).originalYear());
    }

    @Test
    void keepsSeveralLanguagesInHeaderOrder() {
        BookMetadata metadata = parser.metadata(new BookText(417, "Language: German, English", ""));

        assertEquals(List.of("de", "en"), metadata.languages());
        assertEquals(List.of("German", "English"), metadata.languageNames());
    }

    @Test
    void keepsALanguageWithoutCodeOnlyAmongTheNames() {
        BookMetadata metadata = parser.metadata(new BookText(5, "Language: Middle English, English", ""));

        assertEquals(List.of("en"), metadata.languages());
        assertEquals(List.of("Middle English", "English"), metadata.languageNames());
    }

    @Test
    void leavesMissingFieldsNullOrEmpty() {
        assertEquals(new BookMetadata(5, "Only a title", null, List.of(), List.of(), List.of(), null, null),
                parser.metadata(new BookText(5, "Title: Only a title", "")));
    }
}
