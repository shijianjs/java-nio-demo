package com.example.javaniodemo.myfuture;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

@Slf4j
public class MyCompletableFutureTest {

    @BeforeEach
    public void setUp() {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
    }


    @SneakyThrows
    @Test
    public void testDelay0() {
        log.info("start");
        final MyCompletableFuture<String> future = apiRequest();
        final String block = future.block();
        log.info("result: " + block);
    }

    @SneakyThrows
    @Test
    public void testDelay1() {
        log.info("start");
        final MyCompletableFuture<String> future = apiRequest()
                .whenComplete((s, throwable) -> log.info("whenComplete: " + s));

        final String block = future.block();
        log.info("result: " + block);
    }

    @SneakyThrows
    @Test
    public void testDelay2() {
        log.info("start");
        final MyCompletableFuture<String> future = apiRequest()
                .whenComplete((s, throwable) -> log.info("whenComplete: " + s))
                .thenCompose(s -> {
                    log.info("进入thenCompose："+s);
                    return MyCompletableFuture.completedFuture("thenCompose: " + s)
                            .delay(3000)
                            .whenComplete((sc, throwable) -> log.info("thenCompose + whenComplete: " + sc));
                });
        final String block = future.block();
        log.info("result: " + block);
    }

    @SneakyThrows
    @Test
    public void testDelay3() {
        log.info("start");
        MyCompletableFuture<String> singleParallelFuture = MyCompletableFuture.completedFuture("");
        singleParallelFuture = singleParallelFuture.thenCompose(s -> apiRequest()
                .whenComplete((s1, throwable) -> {
                    Assertions.assertEquals("hello", s1);
                    log.info("进入whenComplete：" + s1);
                    // countRequest(counter, i);
                }));
        final String block = singleParallelFuture.block();
        log.info("result: " + block);
    }

    @SneakyThrows
    @Test
    public void testDelay4() {
        log.info("start");
        int requestsPerParallel = 2;
        MyCompletableFuture<String> singleParallelFuture = MyCompletableFuture.completedFuture("");
        for (int j = 0; j < requestsPerParallel; j++) {
            singleParallelFuture = singleParallelFuture.thenCompose(s -> apiRequest()
                    .whenComplete((s1, throwable) -> {
                        Assertions.assertEquals("hello", s1);
                        log.info("进入whenComplete：" + s1);
                        // countRequest(counter, i);
                    }));
        }
        final String block = singleParallelFuture.block();
        log.info("result: " + block);
    }

    @Test
    public void multiTest() throws Exception {
        int parallelCount = 100;
        int requestsPerParallel = 2;

        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        System.out.println("开始执行");

        final MyCompletableFuture<Void> resultFuture = MyCompletableFuture.allOf(IntStream.rangeClosed(1, parallelCount)
                        .boxed()
                        .map(i -> {
                            MyCompletableFuture<String> singleParallelFuture = MyCompletableFuture.completedFuture("");
                            for (int j = 0; j < requestsPerParallel; j++) {
                                singleParallelFuture = singleParallelFuture.thenCompose(s -> apiRequest()
                                        .whenComplete((s1, throwable) -> {
                                            Assertions.assertEquals("hello", s1);
                                            countRequest(counter, i);
                                        }));
                            }
                            return singleParallelFuture;
                        })
                        .collect(Collectors.toList())
                        .toArray(new MyCompletableFuture[]{}))
                .whenComplete((unused, throwable) -> {
                    final long duration = (System.currentTimeMillis() - start) / 1000;
                    log.info("请求成功：" + counter + "，耗时s：" + duration);
                });

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultFuture.block();
    }

    public MyCompletableFuture<String> apiRequest() {
        return MyCompletableFuture.completedFuture("hello").delay(5 * 1000);
    }
}
