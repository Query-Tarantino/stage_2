package org.ulpgc.tarantino.bench.run;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void readsEachFactorInNameOrderAndTheRepetitionOfATrial() {
        Map<String, String> factors = TrialName.factors("replicas-2-workers-8-rep-3");

        assertEquals(List.of("replicas", "workers"), List.copyOf(factors.keySet()));
        assertEquals(List.of("2", "8"), List.copyOf(factors.values()));
        assertEquals(3, TrialName.repetition("replicas-2-workers-8-rep-3"));
    }

    @Test
    void ordersTrialsByTheNumericValueOfEachFactorAndThenByRepetition() {
        List<String> trials = List.of("replicas-1-workers-16-rep-1", "replicas-2-workers-8-rep-1", "replicas-1-workers-8-rep-10",
                "replicas-1-workers-4-rep-1", "replicas-1-workers-8-rep-2");

        assertEquals(List.of("replicas-1-workers-4-rep-1", "replicas-1-workers-8-rep-2", "replicas-1-workers-8-rep-10",
                "replicas-1-workers-16-rep-1", "replicas-2-workers-8-rep-1"), trials.stream().sorted(TrialName.ORDER).toList());
    }
}
