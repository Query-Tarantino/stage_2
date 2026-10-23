package org.ulpgc.tarantino.common.startup;

import com.mongodb.client.MongoDatabase;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.HazelcastClients;
import org.ulpgc.tarantino.common.hazelcast.HazelcastConnection;
import org.ulpgc.tarantino.common.http.Dependency;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;

import java.util.List;

/** The Mongo database and the Hazelcast client of a service, opened without waiting for either to answer. */
public record Connections(String mongoUri, MongoDatabase database, HazelcastConnection hazelcast) {

    public static Connections open(TarantinoConfig config) {
        return new Connections(config.mongoUri(), MongoDatabases.database(config.mongoUri()),
                HazelcastClients.connect(config.hazelcastMembers(), config.hazelcastCluster()));
    }

    /** Mongo and Hazelcast, in the order {@code /health} lists them in {@code down}. */
    public List<Dependency> dependencies() {
        return List.of(new Dependency(Health.MONGO, () -> MongoDatabases.answers(mongoUri)),
                new Dependency(Health.HAZELCAST, hazelcast::connected));
    }
}
