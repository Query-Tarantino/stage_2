package org.ulpgc.tarantino.common.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ServerDescription;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MongoDatabases {

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
        return CLIENTS.computeIfAbsent(connectionUri, MongoClients::create);
    }

    private static String databaseName(String connectionUri) {
        return Optional.ofNullable(new ConnectionString(connectionUri).getDatabase()).orElse(DEFAULT_DATABASE);
    }
}
