package org.ulpgc.tarantino.bench.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialNameTest {

    @Test
    void acceptsOnlyFactorValuePairsFollowedByTheRepetition() {
        assertTrue(TrialName.isValid("replicas-2-rep-1"));
        assertTrue(TrialName.isValid("replicas-1-workers-16-rep-3"));
        assertFalse(TrialName.isValid("replicas-2"));
        assertFalse(TrialName.isValid("replicas-rep-1"));
        assertFalse(TrialName.isValid("replicas-2-rep-0"));
        assertFalse(TrialName.isValid("../replicas-2-rep-1"));
    }

    @Test
    void namesTheConfigurationOfATrialAsSummaryCsvDoes() {
        assertEquals("replicas=2", TrialName.configuration("replicas-2-rep-3"));
        assertEquals("replicas=1,workers=16", TrialName.configuration("replicas-1-workers-16-rep-1"));
        assertThrows(IllegalArgumentException.class, () -> TrialName.configuration("replicas-2"));
    }
}
