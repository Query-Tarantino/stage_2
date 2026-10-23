package org.ulpgc.tarantino.indexer.ports.datamarts;

import org.ulpgc.tarantino.indexer.model.book.Book;

public interface MetadataStorage {

    void save(Book book);
}
