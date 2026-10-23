package org.ulpgc.tarantino.common.config;

import java.io.Serial;

public class InvalidConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String variable;

    public InvalidConfigurationException(String variable, String reason) {
        super(variable + " " + reason);
        this.variable = variable;
    }

    public String variable() {
        return variable;
    }
}
