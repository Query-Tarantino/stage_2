package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A database of its own for the tests that need MongoDB, registered with {@code @RegisterExtension} and dropped after
 * every test that used it. It lives on the server of TARANTINO_MONGO_URI (localhost:27017 by default) when it answers,
 * such as a native install; otherwise in a disposable mongo:7.0 container when Docker runs; otherwise the tests that
 * ask for it are skipped.
 */
public final class TemporaryMongoDatabase implements AfterEachCallback {

    private static final String CONFIGURED_SERVER =
            System.getenv().getOrDefault("TARANTINO_MONGO_URI", "mongodb://localhost:27017");
    private static final int ANSWER_SECONDS = 2;

    private final String name = "tarantino_test_" + UUID.randomUUID().toString().replace("-", "");
    private String uri;

    /** The URI of the database; skips the calling test when there is no MongoDB to run it on. */
    public String uri() {
        assumeTrue(Server.URI.isPresent(), "No MongoDB answers at " + CONFIGURED_SERVER + " and Docker is not running");
        if (uri == null) {
            uri = withDatabase(Server.URI.get(), name);
        }
        return uri;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (uri != null) {
            try (MongoClient client = MongoClients.create(uri)) {
                client.getDatabase(name).drop();
            }
            uri = null;
        }
    }

    /** The same connection string with another database: hosts, credentials and options are kept. */
    private static String withDatabase(String connectionUri, String database) {
        int path = connectionUri.indexOf('/', connectionUri.indexOf("://") + "://".length());
        if (path < 0) {
            return connectionUri + "/" + database;
        }
        int options = connectionUri.indexOf('?', path);
        return connectionUri.substring(0, path) + "/" + database + (options < 0 ? "" : connectionUri.substring(options));
    }

    /** Looked for once per process, by the first test that needs MongoDB. */
    private static final class Server {

        private static final Optional<String> URI = find();

        private static Optional<String> find() {
            if (answers(CONFIGURED_SERVER)) {
                return Optional.of(CONFIGURED_SERVER);
            }
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return Optional.empty();
            }
            MongoDBContainer container = new MongoDBContainer("mongo:7.0");
            container.start();
            return Optional.of(container.getConnectionString());
        }

        private static boolean answers(String connectionUri) {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionUri))
                    .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(ANSWER_SECONDS, TimeUnit.SECONDS))
                    .build();
            try (MongoClient client = MongoClients.create(settings)) {
                client.getDatabase("admin").runCommand(new Document("ping", 1));
                return true;
            } catch (MongoException e) {
                return false;
            }
        }
    }
}
