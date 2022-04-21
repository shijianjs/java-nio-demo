package com.example.javaniodemo.demo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;
import static io.netty.util.CharsetUtil.UTF_8;

/**
 * netty的正确用法 😉
 * 不能调用future.sync()
 *
 * 纯netty写法，这里仅仅是和CompletableFuture做了对接，所以调度逻辑和jdk11 http client相同
 * 明显可以看出，netty的写法更复杂，并且状态不好处理
 */
@Slf4j
public class NettyNioDemo implements ApiRequest<CompletableFuture<String>>{

    final AttributeKey<CompletableFuture<String>> resultFutureKey = AttributeKey.valueOf("resultFutureKey");
    Bootstrap nettyHttpClient;

    @BeforeEach
    void setUp() {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
        nettyHttpClient = singleThreadClient();
    }

    @Test
    public void singleTest() throws Exception {
        final CompletableFuture<String> resultFuture = apiRequest()
                .whenComplete((s, throwable) -> log.info("执行结果：" + s));
        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultFuture.get();
    }

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

    public CompletableFuture<String> apiRequest() {
        final CompletableFuture<String> resultFuture = new CompletableFuture<>();
        final ChannelFuture channelFuture = nettyHttpClient.connect("localhost", 8080);
        channelFuture.addListener(future -> {
            final Channel channel = ((ChannelFuture) future).channel();
            channel.attr(resultFutureKey).set(resultFuture);
            URI uri = new URI("http://localhost:8080/delay5s");
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toASCIIString());
            channel.write(request);
            channel.flush();
        });
        return resultFuture;
    }

    private Bootstrap singleThreadClient() {
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        final Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        if (msg instanceof HttpResponse) {
                                            HttpResponse response = (HttpResponse) msg;
                                            // log.info("CONTENT_TYPE:" + response.headers().get(HttpHeaders.Names.CONTENT_TYPE));
                                        } else if (msg instanceof HttpContent) {
                                            HttpContent content = (HttpContent) msg;
                                            ByteBuf buf = content.content();
                                            final String result = buf.toString(UTF_8);
                                            buf.release();
                                            // log.info(result);
                                            final CompletableFuture<String> resultFuture = ctx.channel().attr(resultFutureKey).get();
                                            resultFuture.complete(result);
                                        } else {
                                            log.info(msg.toString());
                                        }
                                    }

                                    @Override
                                    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                        super.channelReadComplete(ctx);
                                        ctx.close();
                                    }
                                });
                    }
                });
        return bootstrap;
    }

}
