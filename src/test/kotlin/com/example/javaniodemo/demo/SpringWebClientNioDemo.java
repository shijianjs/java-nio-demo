package com.example.javaniodemo.demo;

import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

/**
 * 基于 reactor netty
 * <p>
 * 大致花费时间：3h
 */
@lombok.extern.slf4j.Slf4j
public class SpringWebClientNioDemo implements ApiRequest<Mono<String>>{

    WebClient webClient;

    @BeforeEach
    void setUp() {
        webClient = singleThreadClient();
    }

    private WebClient singleThreadClient() {
        final ReactorResourceFactory reactorResourceFactory = new ReactorResourceFactory();
        reactorResourceFactory.setUseGlobalResources(false);
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        reactorResourceFactory.setLoopResources(b -> eventLoopGroup);
        reactorResourceFactory.afterPropertiesSet();
        final WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorResourceFactory, c -> c))
                .build();
        return webClient;
    }

    @Test
    public void singleTest() {
        final Mono<String> mono = apiRequest()
                .doOnNext(log::info);

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        mono.block();
    }

    public Mono<String> apiRequest() {
        return webClient.get()
                .uri("http://localhost:8080/delay5s")
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 试验过的另一个调度逻辑
     * // mono是冷触发的回调，所以可以用同一个，和jdk的CompletableFuture有所区别，这里为了写法相同放下面了
     * // final Mono<String> singleRequestMono = apiRequest()
     * //         .doOnNext(result1 -> {
     * //             Assertions.assertEquals("hello",result1);
     * //             countRequest(counter, i);
     * //         });
     * <p>
     * // 试验过错误的写法：
     * // 下面这个是完全并发的逻辑，
     * // 总体效果是200并发，每个并发跑1次；
     * // 而我们需要的效果是100并发，每个并发跑两次；
     * // return Flux.fromStream(IntStream.rangeClosed(1, 2).boxed())
     * //         .flatMap(j -> singleRequestMono);
     */
    @Test
    public void multiTest() {
        int parallelCount = 100;
        int requestsPerParallel = 2;


        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        log.info("开始执行");

        // 100并发
        final Mono<?> resultMono = Flux.fromStream(IntStream.rangeClosed(1, parallelCount).boxed())
                .flatMap(i -> {
                    Mono<?> singleParallelMono = Mono.just("");
                    for (int j = 0; j < requestsPerParallel; j++) {
                        // 需要这些request按顺序执行，一个执行成功后再执行下一个
                        // 可能是我没找对方法，非常蹩脚的调度逻辑，看上去很不靠谱，但符合预期
                        singleParallelMono = singleParallelMono.flatMap(s -> apiRequest()
                                .doOnNext(result -> {
                                    Assertions.assertEquals("hello", result);
                                    countRequest(counter, i);
                                }));
                    }
                    return singleParallelMono;
                })
                .collectList()
                .doOnNext(result -> {
                    final long duration = (System.currentTimeMillis() - start) / 1000;
                    log.info("请求成功：" + counter + "，耗时s：" + duration);
                });
        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultMono.block();
    }

}
