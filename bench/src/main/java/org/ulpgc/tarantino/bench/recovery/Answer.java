package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;

/** The status and JSON body of an answer from nginx. */
public record Answer(int status, JsonNode body) {

    public static final int OK = 200;

    public boolean ok() {
        return status == OK;
    }
}
