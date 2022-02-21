package com.example.javaniodemo.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

/**
 * 直接使用java nio，不依赖任何外部库
 * <p>
 * 返回CompletableFuture，调度逻辑同jdk11Http
 */
public class JavaNioDemo implements ApiRequest<CompletableFuture<String>> {

    Selector selector;

    @BeforeEach
    void setUp() throws Exception {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
        // client = singleThreadClient2();

        selector = singleThreadClient();
    }

    /**
     *
     *
     * @return
     * @throws Exception
     */
    private Selector singleThreadClient() throws Exception {
        final Selector selector = Selector.open();

        // 手写事件循环
        new Thread(() -> {
            try {

                AtomicInteger loopCount = new AtomicInteger();
                // 这里有个很严重的问题，事件循环空转，不过这个是demo级的，就不处理了
                while (true) {
                    loopCount.incrementAndGet();
                    System.out.println("loopCount: " + loopCount.get());
                    selector.select(key -> {
                        try {
                            // System.out.println("loopCount: " + loopCount.get()+", key: "+key);
                            final SelectorAttachment attachment = (SelectorAttachment) key.attachment();
                            if(!key.isValid()){
                                return;
                            }
                            if (key.isConnectable()) {
                                // System.out.println("isConnectable  "+key);
                                final SocketChannel channel = (SocketChannel) key.channel();
                                // System.out.println(channel.isConnected());
                                // System.out.println(channel.isConnectionPending());
                                channel.finishConnect();
                                // System.out.println(channel.isConnected());
                                // System.out.println(channel.isConnectionPending());
                                channel.register(selector, SelectionKey.OP_WRITE, attachment);
                            }
                            if(!key.isValid()){
                                return;
                            }
                            if (key.isReadable()) {
                                // System.out.println("read  "+key);
                                final SocketChannel channel = (SocketChannel) key.channel();
                                ByteBuffer buffer = ByteBuffer.allocate(256);
                                channel.read(buffer);
                                // System.out.println(buffer);
                                buffer.flip();
                                final byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes);
                                final String responseStr = new String(bytes);
                                // System.out.println(responseStr);
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
                                    attachment.resultFuture.complete(body);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    attachment.resultFuture.completeExceptionally(e);
                                }
                            }
                            if(!key.isValid()){
                                return;
                            }
                            if (key.isWritable()) {
                                // System.out.println("write "+attachment.writeDone+" key"+key);
                                if(!attachment.writeDone){
                                    final SocketChannel channel = (SocketChannel) key.channel();
                                    // final String reqStr = "GET / HTTP/1.1\n\n";
                                    final String reqStr = "GET /delay5s HTTP/1.1\n\n";
                                    final byte[] reqStrBytes = reqStr.getBytes(StandardCharsets.UTF_8);
                                    final ByteBuffer outBuffer = ByteBuffer.wrap(reqStrBytes);
                                    final SelectionKey readKey = channel.register(selector, SelectionKey.OP_READ, attachment);
                                    // System.out.println("readkey  "+readKey);
                                    // selector.wakeup();
                                    final int write = channel.write(outBuffer);
                                    attachment.writeDone = true;
                                    // System.out.println("write"+write);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).start();
        return selector;
    }



    @Test
    public void singleTest() throws Exception {
        final CompletableFuture<String> future = apiRequest()
                .whenComplete((s, throwable) -> System.out.println(s));
        // System.out.println("future生成完毕");

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        future.get();
    }

    static class SelectorAttachment {
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        boolean writeDone = false;
    }

    public CompletableFuture<String> apiRequest() {
        final SelectorAttachment attachment = new SelectorAttachment();
        try {
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            // 一下子全部注册上去会导致循环空转，循环空转会导致单核cpu 100%
            // reqChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT, attachment);
            // 每走一步更新一下注册，可以解决循环空转问题
            channel.register(selector, SelectionKey.OP_CONNECT, attachment);
            channel.connect(new InetSocketAddress("localhost", 8080));
            selector.wakeup();
        } catch (Exception e) {
            e.printStackTrace();
            attachment.resultFuture.completeExceptionally(e);
        }
        return attachment.resultFuture;
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
        System.out.println("开始执行");

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
                    System.out.println("请求成功：" + counter + "，耗时s：" + duration);
                });

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultFuture.get();
    }

}
