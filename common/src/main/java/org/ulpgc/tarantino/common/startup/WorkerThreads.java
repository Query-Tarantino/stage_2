package org.ulpgc.tarantino.common.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntConsumer;

/**
 * The long-lived threads of a service: its workers and its periodic tasks (SPEC §2.2). If one of them dies with
 * anything the task does not handle itself, the service exits with code {@value #DEAD_THREAD}, as a JVM that runs out
 * of memory does (SPEC §2.4). Otherwise {@code /health} would keep answering {@code UP} with that thread gone for good,
 * and a scheduler would simply stop running the task.
 */
public final class WorkerThreads {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerThreads.class);
    private static final int DEAD_THREAD = 1;

    private WorkerThreads() {
    }

    /** Starts a worker on a platform thread with that name. */
    public static Thread start(String name, Runnable worker) {
        return start(name, worker, Runtime.getRuntime()::halt);
    }

    static Thread start(String name, Runnable worker, IntConsumer exit) {
        return Thread.ofPlatform().name(name).start(exitingIfItDies(worker, exit));
    }

    /** The task, for a scheduler or a thread, exiting the service if anything escapes it. */
    public static Runnable exitingIfItDies(Runnable task) {
        return exitingIfItDies(task, Runtime.getRuntime()::halt);
    }

    static Runnable exitingIfItDies(Runnable task, IntConsumer exit) {
        return () -> {
            try {
                task.run();
            } catch (Throwable death) {
                LOGGER.error("Thread {} died, so the service exits", Thread.currentThread().getName(), death);
                exit.accept(DEAD_THREAD);
            }
        };
    }
}
