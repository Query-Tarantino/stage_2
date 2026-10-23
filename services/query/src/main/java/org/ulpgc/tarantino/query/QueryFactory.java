package org.ulpgc.tarantino.query;

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

    public static SearchCommand searchCommand(QueryConfig config) {
        return new SearchCommand(invertedIndex(config), metadata(config), tokenizer(config));
    }

    public static InvertedIndexReader invertedIndex(QueryConfig config) {
        return new MongodbIndexReader(config.mongoUri());
    }

    public static MetadataReader metadata(QueryConfig config) {
        return new MongodbMetadataReader(config.mongoUri());
    }

    private static Tokenizer tokenizer(QueryConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
