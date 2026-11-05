package org.ulpgc.tarantino.bench.broker;

/** One message of {@code bench broker}: when it was sent and received, by {@code System.nanoTime()}, and by which consumer. */
record BrokerSample(long sentNanos, long receivedNanos, int consumer) {

    private static final long NANOS_PER_MICRO = 1_000;

    long latencyMicros() {
        return (receivedNanos - sentNanos) / NANOS_PER_MICRO;
    }

    String row() {
        return sentNanos + "," + receivedNanos + "," + latencyMicros() + "," + consumer;
    }
}
