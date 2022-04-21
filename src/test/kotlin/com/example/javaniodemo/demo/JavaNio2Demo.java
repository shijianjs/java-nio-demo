package com.example.javaniodemo.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

/**
 * 直接使用java nio，不依赖任何外部库
 *
 * 返回CompletableFuture，调度逻辑同jdk11Http
 */
@lombok.extern.slf4j.Slf4j
public class JavaNio2Demo implements ApiRequest<CompletableFuture<String>> {

    AsynchronousChannelGroup client;

    @BeforeEach
    void setUp() throws Exception {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
        client = singleThreadClient();
    }

    private AsynchronousChannelGroup singleThreadClient() throws Exception {
        return AsynchronousChannelGroup.withFixedThreadPool(1, Thread::new);
    }

    @Test
    public void singleTest() throws Exception {
        final CompletableFuture<String> future = apiRequest()
                .whenComplete((s, throwable) -> log.info(s));

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        future.get();
    }

    public CompletableFuture<String> apiRequest() {
        final CompletableFuture<String> resultFuture = new CompletableFuture<>();

        try {
            final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(client);
            channel.connect(new InetSocketAddress("localhost", 8080), null, new CompletionHandler<Void, Object>() {
                @Override
                public void completed(Void result, Object attachment) {
                    final String reqStr = "GET /delay5s HTTP/1.1\n\n";
                    final byte[] reqStrBytes = reqStr.getBytes(StandardCharsets.UTF_8);
                    final ByteBuffer outBuffer = ByteBuffer.wrap(reqStrBytes);
                    channel.write(outBuffer, null, new CompletionHandler<Integer, Object>() {
                        @Override
                        public void completed(Integer result, Object attachment) {
                            // log.info("channel.write completed: " + result);
                            Assertions.assertEquals(reqStrBytes.length, result);
                            ByteBuffer buffer = ByteBuffer.allocate(256);
                            // 这里代码处理的不严谨，半包、粘包等都没考虑
                            channel.read(buffer, null, new CompletionHandler<Integer, Object>() {
                                @Override
                                public void completed(Integer result, Object attachment) {
                                    // log.info(result);
                                    // log.info(buffer);
                                    buffer.flip();
                                    final byte[] bytes = new byte[buffer.remaining()];
                                    buffer.get(bytes);
                                    final String responseStr = new String(bytes);
                                    // log.info(responseStr);
                                    final String[] split = responseStr.split("\r\n\r\n", 2);
                                    String header = split[0];
                                    String body = split[1];
                                    final Integer bodySize = header.lines()
                                            .filter(s -> s.startsWith("Content-Length:"))
                                            .map(s -> s.split(":")[1].trim())
                                            .map(s -> Integer.parseInt(s))
                                            .findFirst()
                                            .get();
                                    Assertions.assertEquals(bodySize, body.getBytes().length);
                                    try {
                                        channel.close();
                                        resultFuture.complete(body);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        resultFuture.completeExceptionally(e);
                                    }
                                }

                                @Override
                                public void failed(Throwable exc, Object attachment) {
                                    log.info("channel.read failed");
                                    exc.printStackTrace();
                                    resultFuture.completeExceptionally(exc);
                                }
                            });

                        }

                        @Override
                        public void failed(Throwable exc, Object attachment) {
                            log.info("channel.write failed");
                            exc.printStackTrace();
                            resultFuture.completeExceptionally(exc);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    log.info("channel.connect failed");
                    exc.printStackTrace();
                    resultFuture.completeExceptionally(exc);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            resultFuture.completeExceptionally(e);
        }
        return resultFuture;
    }


    /**
     * 因为都是回调式写法，整体调度逻辑和SpringWebClient类似
     *
     * @throws Exception
     */
    @Test
    public void multiTest() throws Exception {
        int parallelCount = 100;
        int requestsPerParallel = 2;

        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        log.info("开始执行");

        final CompletableFuture<Void> resultFuture = CompletableFuture.allOf(IntStream.rangeClosed(1, parallelCount)
                        .boxed()
                        .map(i -> {
                            CompletableFuture<String> singleParallelFuture = CompletableFuture.completedFuture("");
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
                        .toArray(new CompletableFuture[]{}))
                .whenComplete((unused, throwable) -> {
                    final long duration = (System.currentTimeMillis() - start) / 1000;
                    log.info("请求成功：" + counter + "，耗时s：" + duration);
                });

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultFuture.get();
    }

}
