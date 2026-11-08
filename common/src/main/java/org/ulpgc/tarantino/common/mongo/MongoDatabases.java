package org.ulpgc.tarantino.common.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ServerDescription;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Opens the database of {@code TARANTINO_MONGO_URI} and tells whether it answers, for {@code /health} (SPEC §4.3, §4.7 and §6.5). */
public final class MongoDatabases {

    /**
     * How long an operation waits for a server before it throws, unless the URI sets {@code serverSelectionTimeoutMS}:
     * with the driver's 30 s, a handler would make a client wait half a minute for its {@code 503} (SPEC §6.7).
     */
    static final Duration SERVER_SELECTION_TIMEOUT = Duration.ofSeconds(2);

    private static final String DEFAULT_DATABASE = "tarantino";
    private static final Map<String, MongoClient> CLIENTS = new ConcurrentHashMap<>();

    private MongoDatabases() {
    }

    public static MongoDatabase database(String connectionUri) {
        return client(connectionUri).getDatabase(databaseName(connectionUri));
    }

    /** Whether a server of the URI answered its last heartbeat, read without waiting so {@code /health} never blocks (SPEC §6.5). */
    public static boolean answers(String connectionUri) {
        return client(connectionUri).getClusterDescription().getServerDescriptions().stream().anyMatch(ServerDescription::isOk);
    }

    private static MongoClient client(String connectionUri) {
        return CLIENTS.computeIfAbsent(connectionUri, MongoDatabases::create);
    }

    private static MongoClient create(String connectionUri) {
        return MongoClients.create(MongoClientSettings.builder()
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .applyConnectionString(new ConnectionString(connectionUri))
                .build());
    }

    private static String databaseName(String connectionUri) {
        return Optional.ofNullable(new ConnectionString(connectionUri).getDatabase()).orElse(DEFAULT_DATABASE);
    }
}
