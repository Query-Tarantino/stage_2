package org.ulpgc.tarantino.query.benchmarking;

/** A line of queries.txt: {@code <category>: <query>} (SPEC §3). */
record WorkloadQuery(String category, String text) {

    private static final String SEPARATOR = ":";

    static WorkloadQuery parse(String line) {
        int separator = line.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("A query must be written as <category>: <query>, found: " + line);
        }
        return new WorkloadQuery(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
    }
}
