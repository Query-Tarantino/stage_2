package org.ulpgc.tarantino.common.config;

/** The four services and their default HTTP ports (SPEC §2.2 and §6.1). */
public enum TarantinoService {
    CRAWLER(7001),
    INDEXER(7002),
    QUERY(7003),
    CONTROL(7004);

    private final int defaultPort;

    TarantinoService(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }
}
