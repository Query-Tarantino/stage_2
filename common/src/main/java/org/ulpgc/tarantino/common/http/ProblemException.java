package org.ulpgc.tarantino.common.http;

import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.Serial;

/** An API error that a handler throws and {@link ProblemDetails} answers with its code's status (SPEC §6.6). */
public class ProblemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ProblemCode code;
    private final String parameter;

    public ProblemException(ProblemCode code, String detail) {
        this(code, null, detail);
    }

    /** {@code parameter} names the request parameter at fault; it only reaches {@code 400} responses. */
    public ProblemException(ProblemCode code, String parameter, String detail) {
        super(detail);
        this.code = code;
        this.parameter = parameter;
    }

    public ProblemCode code() {
        return code;
    }

    public String parameter() {
        return parameter;
    }
}
