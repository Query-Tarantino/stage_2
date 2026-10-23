package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.adapters.index.mongo.MongodbIndexReader;
import org.ulpgc.tarantino.query.adapters.metadata.MongodbMetadataReader;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;
import org.ulpgc.tarantino.query.ports.MetadataReader;

public final class QueryFactory {

    private QueryFactory() {
    }

    public static SearchCommand searchCommand(TarantinoConfig config) {
        return new SearchCommand(invertedIndex(config), metadata(config), tokenizer(config));
    }

    public static InvertedIndexReader invertedIndex(TarantinoConfig config) {
        return new MongodbIndexReader(config.mongoUri());
    }

    public static MetadataReader metadata(TarantinoConfig config) {
        return new MongodbMetadataReader(config.mongoUri());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
