package org.ulpgc.tarantino.common.messaging;

import org.ulpgc.tarantino.common.model.BatchPublished;

import java.util.function.Consumer;

/** The {@code published-batches} topic of SPEC §4.2. */
public interface BatchAnnouncements {

    void publish(BatchPublished batch);

    void listen(Consumer<BatchPublished> listener);
}
