package org.example.demo.loom.loomtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LoomTest {
    @BeforeEach
    void setUp() throws Exception {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
    }

    @Test
    public void officialDemo1() throws Exception {
        Thread thread = Thread.ofVirtual().start(() -> System.out.println("Hello"));
        thread.join();
    }

    @Test
    public void officialDemo2() throws Exception {
        var queue = new SynchronousQueue<String>();

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(2));
                queue.put("done");
            } catch (InterruptedException e) {
            }
        });

        String msg = queue.take();
        System.out.println(msg);
    }

    @Test
    public void officialDemo3() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Submits a value-returning task and waits for the result
            Future<String> future = executor.submit(() -> "foo");
            String result = future.get();

            // Submits two value-returning tasks to get a Stream that is lazily populated
            // with completed Future objects as the tasks complete
            // Stream<Future<String>> stream = executor.submit(List.of(() -> "foo", () -> "bar"));
            // stream.filter(Future::isDone)
            //         .map(Future::get)
            //         .forEach(System.out::println);

            // Executes two value-returning tasks, waiting for both to complete
            List<Future<String>> results1 = executor.invokeAll(List.of(() -> "foo", () -> "bar"));

            // Executes two value-returning tasks, waiting for both to complete. If one of the
            // tasks completes with an exception, the other is cancelled.
            // List<Future<String>> results2 = executor.invokeAll(List.of(() -> "foo", () -> "bar"), /*waitAll*/ false);

            // Executes two value-returning tasks, returning the result of the first to
            // complete, cancelling the other.
            String first = executor.invokeAny(List.of(() -> "foo", () -> "bar"));

        }
    }


    @Test
    public void myDemo() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        IntStream.rangeClosed(1, 100).boxed()
                .map(i -> executor.submit(() -> {
                    for (int j = 0; j < 5; j++) {
                        Thread.sleep(5000);
                        System.out.println("i=" + i + ", j=" + j+", thread count: "+Thread.activeCount());
                    }
                    return "exe: " + i;
                }))
                .toList()
                .forEach(f -> {
                    try {
                        System.out.println("result: " + f.get());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }
}
