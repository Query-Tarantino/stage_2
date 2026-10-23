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

public final class QueryFactory {

    private QueryFactory() {
    }

    public static SnapshotRefresher snapshotRefresher(Connections connections) {
        return new SnapshotRefresher(new MongoPublishedBatches(connections.database()));
    }

    public static SearchRoutes searchRoutes(TarantinoConfig config, Connections connections, SnapshotRefresher snapshots) {
        SearchCommand search = new SearchCommand(new MongoPostingsReader(connections.database()),
                new MongoMetadataReader(connections.database()), tokenizer(config));
        return new SearchRoutes(snapshots, search);
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
