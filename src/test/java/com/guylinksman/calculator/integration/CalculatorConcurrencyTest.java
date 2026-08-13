package com.guylinksman.calculator.integration;

import com.guylinksman.calculator.Calculator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calculator.run(...) builds its own Tokenizer/Evaluator/Environment on every call
 * (DESIGN "Concurrency") - there is no shared mutable state between invocations -
 * so concurrent calls on independent inputs should never interfere with each other.
 * This proves that claim rather than just asserting it in prose: many threads each
 * run a script whose expected result depends on that thread's own index, so any
 * cross-thread state leak (e.g. a shared Environment) surfaces as a wrong value for
 * at least one thread instead of passing by coincidence.
 */
class CalculatorConcurrencyTest {

    private static final int TASK_COUNT = 200;
    private static final int THREAD_POOL_SIZE = 16;

    @Test
    void concurrentIndependentRunsDoNotInterfereWithEachOther()
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch startingGate = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>(TASK_COUNT);

        try {
            for (int i = 0; i < TASK_COUNT; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    startingGate.await();
                    return Calculator.run(List.of(
                            "x = " + index,
                            "y = x * 2 + 1",
                            "z = y - x"));
                }));
            }

            startingGate.countDown(); // release every task at once to maximize overlap

            for (int i = 0; i < TASK_COUNT; i++) {
                String expected = "(x=" + i + ",y=" + (2 * i + 1) + ",z=" + (i + 1) + ")";
                assertEquals(expected, results.get(i).get(10, TimeUnit.SECONDS),
                        "task for index " + i + " produced a result that doesn't match its own input");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
