package org.ulpgc.tarantino.indexer;

import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.BatchPublishedTopic;
import org.ulpgc.tarantino.common.operations.Replica;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.hazelcast.HazelcastIndexQueue;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoBatchStore;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.commands.BatchCollector;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.commands.IndexWorker;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Clock;

public final class IndexerFactory {

    private IndexerFactory() {
    }

    public static IndexWorker indexWorker(TarantinoConfig config, Connections connections) {
        HazelcastInstance client = connections.hazelcast().client();
        BatchCollector collector = new BatchCollector(new HazelcastIndexQueue(client), config.indexBatch(), config.batchTimeout(),
                Clock.systemUTC());
        Tokenizer tokenizer = tokenizer(config);
        IndexBatchCommand index = new IndexBatchCommand(new MongoBatchStore(connections.database(), config.lease(), Replica.name()),
                datalakeReader(config), new HeaderParser(tokenizer), tokenizer, new MongoIndexWriter(connections.database()),
                new BatchPublishedTopic(client)::publish, new TransitionLog());
        return new IndexWorker(collector, index);
    }

    public static DatalakeReader datalakeReader(TarantinoConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
