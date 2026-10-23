package org.ulpgc.tarantino.control.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepReportTest {

    @Test
    void describesTheActionBookAndOutcome() {
        StepReport report = new StepReport(NextStep.download(1342), Outcome.success("stored in datalake/1342"));

        assertEquals("DOWNLOAD 1342: stored in datalake/1342", report.description());
        assertFalse(report.idle());
    }

    @Test
    void summarizesTheBooksOfABatch() {
        StepReport report = new StepReport(NextStep.index(List.of(84, 1342, 2701)), Outcome.success("3 indexed"));

        assertEquals("INDEX 3 books (84…2701): 3 indexed", report.description());
    }

    @Test
    void isIdleOnlyForIdleSteps() {
        assertTrue(new StepReport(NextStep.idle(), Outcome.success("nothing left to do")).idle());
    }
}
