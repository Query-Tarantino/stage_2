package org.ulpgc.tarantino.common.startup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerThreadsTest {

    @Test
    void exitsWithCodeOneWhenAWorkerDiesOfAnError() throws InterruptedException {
        List<Integer> exitCodes = new CopyOnWriteArrayList<>();

        WorkerThreads.start("index-batches", () -> {
            throw new StackOverflowError();
        }, exitCodes::add).join();

        assertEquals(List.of(1), exitCodes);
    }

    @Test
    void exitsWhenAPeriodicTaskThrowsInsteadOfLettingTheSchedulerDropIt() throws Exception {
        List<Integer> exitCodes = new CopyOnWriteArrayList<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(WorkerThreads.exitingIfItDies(() -> {
            throw new IllegalStateException("reconciliation bug");
        }, exitCodes::add), 0, TimeUnit.MILLISECONDS).get();
        scheduler.shutdown();

        assertEquals(List.of(1), exitCodes);
    }

    @Test
    void letsAWorkerThatReturnsEndWithoutExiting() throws InterruptedException {
        List<Integer> exitCodes = new CopyOnWriteArrayList<>();

        WorkerThreads.start("download-0", () -> {
        }, exitCodes::add).join();

        assertEquals(List.of(), exitCodes);
    }
}
