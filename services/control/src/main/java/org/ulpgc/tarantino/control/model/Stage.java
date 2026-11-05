package org.ulpgc.tarantino.control.model;

import org.ulpgc.tarantino.common.model.BookStatus;

/** A stage the orchestrator dispatches to, with the state its books wait in (SPEC §11.4). */
public enum Stage {
    DOWNLOAD(BookStatus.QUEUED),
    INDEX(BookStatus.DOWNLOADED);

    private final BookStatus waitingStatus;

    Stage(BookStatus waitingStatus) {
        this.waitingStatus = waitingStatus;
    }

    public BookStatus waitingStatus() {
        return waitingStatus;
    }
}
