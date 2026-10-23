package org.ulpgc.tarantino.common.mongo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDatabasesTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void useTheUriDatabaseOrDefaultToTarantino() {
        assertEquals("tarantino", MongoDatabases.database("mongodb://localhost:27017").getName());
        assertEquals("tarantino_benchmark", MongoDatabases.database("mongodb://localhost:27017/tarantino_benchmark").getName());
    }

    @Test
    void answersOnceItsServerHasAnswered() throws InterruptedException {
        String uri = mongo.uri();
        Instant deadline = Instant.now().plusSeconds(10);
        while (!MongoDatabases.answers(uri) && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }

        assertTrue(MongoDatabases.answers(uri));
    }

    @Test
    void doesNotAnswerWithoutAServer() {
        assertFalse(MongoDatabases.answers("mongodb://127.0.0.1:1"));
    }
}
