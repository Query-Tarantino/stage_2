package org.ulpgc.tarantino.indexer.benchmarking.support;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

final class MongoStores {

    private static final String DEFAULT_INDEX = "_id_";
    private static final Document FLUSH_TO_DISK = new Document("fsync", 1);
    private static final List<Document> STORAGE_STATS =
            List.of(new Document("$collStats", new Document("storageStats", new Document())));

    private MongoStores() {
    }

    static void drop(String connectionUri) {
        try (MongoClient client = MongoClients.create(connectionUri)) {
            database(client, connectionUri).drop();
        }
    }

    static void copy(String sourceUri, String targetUri) {
        try (MongoClient client = MongoClients.create(sourceUri)) {
            MongoDatabase source = database(client, sourceUri);
            MongoDatabase target = database(client, targetUri);
            source.listCollectionNames().forEach(collection -> copyCollection(source.getCollection(collection), target));
        }
    }

    /** Puts back the documents of some values of a unique field as the snapshot has them, deleting the others. */
    static void restoreDocuments(String snapshotUri, String targetUri, String collection, String field,
                                 Collection<String> values) {
        try (MongoClient client = MongoClients.create(snapshotUri)) {
            Bson documentsOfValues = Filters.in(field, values);
            MongoCollection<Document> target = database(client, targetUri).getCollection(collection);
            target.deleteMany(documentsOfValues);
            List<Document> stored = database(client, snapshotUri).getCollection(collection).find(documentsOfValues)
                    .into(new ArrayList<>());
            if (!stored.isEmpty()) {
                target.insertMany(stored, new InsertManyOptions().ordered(false));
            }
        }
    }

    static long documentCount(String connectionUri, String collection) {
        try (MongoClient client = MongoClients.create(connectionUri)) {
            return database(client, connectionUri).getCollection(collection).countDocuments();
        }
    }

    static long diskUsage(String connectionUri) {
        try (MongoClient client = MongoClients.create(connectionUri)) {
            client.getDatabase("admin").runCommand(FLUSH_TO_DISK);
            MongoDatabase database = database(client, connectionUri);
            return StreamSupport.stream(database.listCollectionNames().spliterator(), false)
                    .mapToLong(collection -> storedBytes(database, collection))
                    .sum();
        }
    }

    private static void copyCollection(MongoCollection<Document> source, MongoDatabase target) {
        String name = source.getNamespace().getCollectionName();
        Document destination = new Document("db", target.getName()).append("coll", name);
        source.aggregate(List.of(new Document("$out", destination))).toCollection();
        source.listIndexes().forEach(index -> copyIndex(index, target.getCollection(name)));
    }

    private static void copyIndex(Document index, MongoCollection<Document> target) {
        if (!DEFAULT_INDEX.equals(index.getString("name"))) {
            IndexOptions options = new IndexOptions().name(index.getString("name")).unique(index.getBoolean("unique", false));
            target.createIndex(index.get("key", Document.class), options);
        }
    }

    private static long storedBytes(MongoDatabase database, String collection) {
        Document result = Objects.requireNonNull(database.getCollection(collection).aggregate(STORAGE_STATS).first());
        Document stats = result.get("storageStats", Document.class);
        return stats.get("storageSize", Number.class).longValue() + stats.get("totalIndexSize", Number.class).longValue();
    }

    private static MongoDatabase database(MongoClient client, String connectionUri) {
        return client.getDatabase(Objects.requireNonNull(new ConnectionString(connectionUri).getDatabase()));
    }
}
