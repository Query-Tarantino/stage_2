package org.ulpgc.tarantino.indexer.commands;

import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.util.Optional;

/** Reads a book from the datalake and parses its header and body, as in Stage 1; empty if it is not there (SPEC §4.6). */
public class BookReader {

    private final DatalakeReader datalake;
    private final HeaderParser headerParser;
    private final Tokenizer tokenizer;

    public BookReader(DatalakeReader datalake, Tokenizer tokenizer) {
        this.datalake = datalake;
        this.headerParser = new HeaderParser(tokenizer);
        this.tokenizer = tokenizer;
    }

    public Optional<ParsedBook> read(int bookId) {
        return datalake.bookText(bookId)
                .map(text -> new ParsedBook(headerParser.metadata(text), tokenizer.frequencies(text.body())));
    }
}
