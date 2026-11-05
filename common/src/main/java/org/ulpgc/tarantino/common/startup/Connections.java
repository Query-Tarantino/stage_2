package org.ulpgc.tarantino.common.startup;

import com.mongodb.client.MongoDatabase;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.http.Dependency;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.Brokers;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** The Mongo database and the broker of a service, if its mode uses one, opened without waiting for either to answer. */
public record Connections(String mongoUri, MongoDatabase database, Optional<Broker> broker) {

    public static Connections open(TarantinoConfig config) {
        return new Connections(config.mongoUri(), MongoDatabases.database(config.mongoUri()), Brokers.open(config));
    }

    /** Mongo and the broker, in the order {@code /health} lists them in {@code down}. */
    public List<Dependency> dependencies() {
        return Stream.concat(Stream.of(new Dependency(Health.MONGO, () -> MongoDatabases.answers(mongoUri))),
                broker.stream().map(open -> new Dependency(open.name(), open::connected))).toList();
    }
}
