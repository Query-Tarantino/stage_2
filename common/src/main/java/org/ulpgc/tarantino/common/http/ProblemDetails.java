package org.ulpgc.tarantino.common.http;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.ProblemCode;

/**
 * Answers every error as Problem Details (RFC 9457, SPEC §6.6): {@code parameter} only in {@code 400} responses,
 * {@code Retry-After} on every {@code 503} and {@code 500} without {@code code} for anything unexpected.
 */
public final class ProblemDetails {

    static final String CONTENT_TYPE = "application/problem+json";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetails.class);
    private static final String TYPE = "about:blank";
    private static final String INTERNAL_SERVER_ERROR_TITLE = "Internal Server Error";

    private ProblemDetails() {
    }

    public static void register(Javalin app) {
        app.exception(ProblemException.class, (problem, context) -> answer(context, problem));
        app.exception(Exception.class, (error, context) -> {
            LOGGER.error("Unexpected error answering {} {}", context.method(), context.path(), error);
            answer(context, HttpStatus.INTERNAL_SERVER_ERROR, null, null, null);
        });
    }

    private static void answer(Context context, ProblemException problem) {
        HttpStatus status = HttpStatus.forStatus(problem.code().status());
        String parameter = status == HttpStatus.BAD_REQUEST ? problem.parameter() : null;
        answer(context, status, problem.code(), parameter, problem.getMessage());
    }

    private static void answer(Context context, HttpStatus status, ProblemCode code, String parameter, String detail) {
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            RetryAfter.add(context);
        }
        Problem problem = new Problem(TYPE, title(status), status.getCode(), code, parameter, detail);
        context.status(status).contentType(CONTENT_TYPE).result(TarantinoJson.write(problem));
    }

    /** The reason phrase of RFC 9110, as {@code about:blank} asks; Javalin shortens the one of 500 to Server Error. */
    private static String title(HttpStatus status) {
        return status == HttpStatus.INTERNAL_SERVER_ERROR ? INTERNAL_SERVER_ERROR_TITLE : status.getMessage();
    }
}
