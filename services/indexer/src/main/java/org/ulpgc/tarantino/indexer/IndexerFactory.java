package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

public final class IndexerFactory {

    private IndexerFactory() {
    }

    public static DatalakeReader datalakeReader(TarantinoConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    public static HeaderParser headerParser(TarantinoConfig config) {
        return new HeaderParser(tokenizer(config));
    }

    public static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
