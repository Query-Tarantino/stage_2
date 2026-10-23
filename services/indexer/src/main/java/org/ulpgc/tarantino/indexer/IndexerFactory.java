package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.index.mongo.MongodbIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.metadata.MongodbMetadataAdapter;
import org.ulpgc.tarantino.indexer.commands.IndexBookCommand;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

public final class IndexerFactory {

    private IndexerFactory() {
    }

    public static IndexBookCommand indexCommand(IndexerConfig config) {
        return new IndexBookCommand(datalakeReader(config), new HeaderParser(), tokenizer(config),
                invertedIndex(config), metadata(config));
    }

    public static DatalakeReader datalakeReader(IndexerConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    public static InvertedIndexStorage invertedIndex(IndexerConfig config) {
        return new MongodbIndexAdapter(config.mongoUri());
    }

    public static MetadataStorage metadata(IndexerConfig config) {
        return new MongodbMetadataAdapter(config.mongoUri());
    }

    private static Tokenizer tokenizer(IndexerConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
