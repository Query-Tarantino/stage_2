package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.adapters.http.SearchRoutes;
import org.ulpgc.tarantino.query.adapters.mongo.MongoMetadataReader;
import org.ulpgc.tarantino.query.adapters.mongo.MongoPostingsReader;
import org.ulpgc.tarantino.query.adapters.mongo.MongoPublishedBatches;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;
import org.ulpgc.tarantino.query.ports.MetadataReader;

public final class QueryFactory {

    private QueryFactory() {
    }

    public static SnapshotRefresher snapshotRefresher(Connections connections) {
        return new SnapshotRefresher(new MongoPublishedBatches(connections.database()),
                new MongoMetadataReader(connections.database()));
    }

    public static SearchRoutes searchRoutes(TarantinoConfig config, Connections connections, SnapshotRefresher snapshots) {
        Tokenizer tokenizer = tokenizer(config);
        MetadataReader metadata = new MongoMetadataReader(connections.database());
        SearchCommand search = new SearchCommand(new MongoPostingsReader(connections.database()), metadata, tokenizer);
        return new SearchRoutes(snapshots, search, metadata, tokenizer, config.corsOrigin());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
