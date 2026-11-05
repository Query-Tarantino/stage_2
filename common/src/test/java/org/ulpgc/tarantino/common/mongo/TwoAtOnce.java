package org.ulpgc.tarantino.common.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/** Runs two calls from two threads released together, as two claimants of a conditional write would. */
public final class TwoAtOnce {

    private TwoAtOnce() {
    }

    /** The same call twice, as two replicas competing for the same write. */
    public static <T> List<T> call(Callable<T> action) throws InterruptedException, ExecutionException {
        return call(action, action);
    }

    /** Two rival writes, such as a release against a renewal; the outcomes come in the order of the calls. */
    public static <T> List<T> call(Callable<T> first, Callable<T> second) throws InterruptedException, ExecutionException {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            List<Future<T>> results = Stream.of(first, second)
                    .map(action -> releasedTogether(start, action))
                    .map(threads::submit)
                    .toList();
            List<T> outcomes = new ArrayList<>();
            for (Future<T> result : results) {
                outcomes.add(result.get());
            }
            return outcomes;
        }
    }

    private static <T> Callable<T> releasedTogether(CyclicBarrier start, Callable<T> action) {
        return () -> {
            start.await();
            return action.call();
        };
    }
}
