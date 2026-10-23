package org.ulpgc.tarantino.crawler.benchmarking.support.environment;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public final class Heap {

    private static final int COLLECTIONS = 3;
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    private Heap() {
    }

    public static long usedAfterFullCollection() {
        for (int collection = 0; collection < COLLECTIONS; collection++) {
            System.gc();
        }
        return MEMORY.getHeapMemoryUsage().getUsed();
    }

    /** Bytes allocated so far by every thread of this JVM, garbage included. */
    public static long allocatedSoFar() {
        return THREADS.getTotalThreadAllocatedBytes();
    }
}
