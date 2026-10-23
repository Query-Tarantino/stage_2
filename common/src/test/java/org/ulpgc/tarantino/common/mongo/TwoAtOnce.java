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

/** Runs the same call from two threads released together, as two claimants of a conditional write would. */
public final class TwoAtOnce {

    private TwoAtOnce() {
    }

    public static <T> List<T> call(Callable<T> action) throws InterruptedException, ExecutionException {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<T> claimant = () -> {
            start.await();
            return action.call();
        };
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            List<Future<T>> results = Stream.of(claimant, claimant).map(threads::submit).toList();
            List<T> outcomes = new ArrayList<>();
            for (Future<T> result : results) {
                outcomes.add(result.get());
            }
            return outcomes;
        }
    }
}
