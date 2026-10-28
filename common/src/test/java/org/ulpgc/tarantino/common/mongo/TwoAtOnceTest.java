package org.ulpgc.tarantino.common.mongo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoAtOnceTest {

    private static final long MEETING_SECONDS = 5;

    @Test
    void runsBothRivalCallsAtTheSameTimeAndKeepsTheirOrder() throws Exception {
        CyclicBarrier bothRunning = new CyclicBarrier(2);

        List<String> outcomes = TwoAtOnce.call(meetingAt(bothRunning, "release"), meetingAt(bothRunning, "renew"));

        assertEquals(List.of("release", "renew"), outcomes);
    }

    private static Callable<String> meetingAt(CyclicBarrier bothRunning, String outcome) {
        return () -> {
            bothRunning.await(MEETING_SECONDS, TimeUnit.SECONDS);
            return outcome;
        };
    }
}
