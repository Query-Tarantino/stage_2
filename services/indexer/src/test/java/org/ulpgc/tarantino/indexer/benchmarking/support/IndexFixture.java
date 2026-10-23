package org.ulpgc.tarantino.indexer.benchmarking.support;

import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.model.book.GutenbergText;
import org.ulpgc.tarantino.indexer.adapters.stopwords.FileStopwordsLoader;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;
import org.ulpgc.tarantino.indexer.model.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class IndexFixture {

    private final BenchmarkDataset dataset;
    private final Tokenizer tokenizer;
    private final HeaderParser headerParser = new HeaderParser();

    public IndexFixture(BenchmarkDataset dataset, Tokenizer tokenizer) {
        this.dataset = dataset;
        this.tokenizer = tokenizer;
    }

    public static IndexFixture fromEnvironment() {
        Path stopwords = BenchmarkPaths.workload().resolve("stopwords.txt");
        return new IndexFixture(BenchmarkDataset.fromEnvironment(), new Tokenizer(new FileStopwordsLoader(stopwords).stopwords()));
    }

    public BenchmarkDataset dataset() {
        return dataset;
    }

    public void index(InvertedIndexStorage invertedIndex, List<Integer> ids) {
        add(invertedIndex, ids);
        invertedIndex.flush();
    }

    public void add(InvertedIndexStorage invertedIndex, List<Integer> ids) {
        ids.stream().map(this::occurrences).forEach(invertedIndex::add);
    }

    public void save(MetadataStorage metadata, List<Book> books) {
        books.forEach(metadata::save);
    }

    public List<Book> books(List<Integer> ids) {
        return ids.stream().map(this::bookText).map(headerParser::book).toList();
    }

    /** Distinct terms of the books, which every index structure must hold once they are indexed. */
    public long vocabularySize(List<Integer> ids) {
        Set<String> vocabulary = new HashSet<>();
        ids.forEach(bookId -> vocabulary.addAll(terms(bookId)));
        return vocabulary.size();
    }

    public Set<String> terms(int bookId) {
        return occurrences(bookId).frequencies().keySet();
    }

    private TermOccurrences occurrences(int bookId) {
        return tokenizer.occurrences(bookId, bookText(bookId).body());
    }

    private BookText bookText(int bookId) {
        org.ulpgc.tarantino.crawler.model.book.BookText split = GutenbergText.bookText(bookId, dataset.rawText(bookId));
        return new BookText(bookId, split.header(), split.body(), Path.of("datalake", bookId + ".body.txt"));
    }
}
