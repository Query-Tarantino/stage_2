package org.ulpgc.tarantino.common.mongo;

import java.io.Serial;

/**
 * {@code index_state.current} does not exist yet, because no service has finished “Create the pointer” (SPEC §5.1).
 * The handlers answer it as {@code 503}, like a dependency that does not answer yet (SPEC §4.5).
 */
public class NoGenerationPointerException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NoGenerationPointerException() {
        super("The generation pointer does not exist yet");
    }
}
