package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MongoDatabases {

    private static final String DEFAULT_DATABASE = "tarantino";
    private static final Map<String, MongoClient> CLIENTS = new ConcurrentHashMap<>();

    private MongoDatabases() {
    }

    public static MongoDatabase database(String connectionUri) {
        return CLIENTS.computeIfAbsent(connectionUri, MongoClients::create).getDatabase(databaseName(connectionUri));
    }

    private static String databaseName(String connectionUri) {
        return Optional.ofNullable(new ConnectionString(connectionUri).getDatabase()).orElse(DEFAULT_DATABASE);
    }
}
