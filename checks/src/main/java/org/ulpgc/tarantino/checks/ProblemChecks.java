package org.ulpgc.tarantino.checks;

import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;

import static org.ulpgc.tarantino.checks.ApiNames.CODE;
import static org.ulpgc.tarantino.checks.ApiNames.PARAMETER;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/** A request that must fail with the Problem Details of SPEC §6.6: the status of its code, the code and the parameter at fault. */
final class ProblemChecks {

    private final CheckedClient client;

    ProblemChecks(CheckedClient client) {
        this.client = client;
    }

    void get(String url, ProblemCode code) throws IOException, InterruptedException {
        assertCode("GET " + url, client.get(url), code);
    }

    void get(String url, ProblemCode code, String parameter) throws IOException, InterruptedException {
        String request = "GET " + url;
        assertParameter(request, assertCode(request, client.get(url), code), parameter);
    }

    void post(String url, ProblemCode code) throws IOException, InterruptedException {
        assertCode("POST " + url, client.post(url, ""), code);
    }

    void post(String url, ProblemCode code, String parameter) throws IOException, InterruptedException {
        String request = "POST " + url;
        assertParameter(request, assertCode(request, client.post(url, ""), code), parameter);
    }

    private static CheckedClient.Response assertCode(String request, CheckedClient.Response response, ProblemCode code) {
        assertStatus(code.status(), response, request);
        assertTrue(response.json() != null && response.json().has(CODE), "the " + code.status() + " of " + request + " has a code");
        assertEquals(code.name(), response.json().get(CODE).asText(), "the code of " + request);
        return response;
    }

    private static void assertParameter(String request, CheckedClient.Response response, String parameter) {
        assertTrue(response.json().has(PARAMETER), "the problem of " + request + " has a parameter");
        assertEquals(parameter, response.json().get(PARAMETER).asText(), "the parameter of " + request);
    }
}
