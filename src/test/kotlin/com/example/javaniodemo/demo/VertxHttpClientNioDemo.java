package com.example.javaniodemo.demo;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

@lombok.extern.slf4j.Slf4j
public class VertxHttpClientNioDemo implements ApiRequest<Future<String>> {

    HttpClient client;

    @BeforeEach
    void setUp() {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
        client = singleThreadClient();
    }

    private HttpClient singleThreadClient() {
        final VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setWorkerPoolSize(1);
        vertxOptions.setEventLoopPoolSize(1);
        Vertx vertx = Vertx.vertx(vertxOptions);
        final HttpClientOptions httpClientOptions = new HttpClientOptions();
        httpClientOptions.setMaxPoolSize(10000);
        return vertx.createHttpClient(httpClientOptions);
    }

    @Test
    public void singleTest() throws Exception {
        final Future<String> future = apiRequest()
                .onSuccess((s) -> log.info(s));

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        // 发现vertx的future并没有提供阻塞当前线程的get方法，这里转换下再get。
        future.toCompletionStage().toCompletableFuture().get();
    }


    /**
     * 直接用core的api了，没用封装后的WebClient，WebClient更好用些
     * @return
     */
    public Future<String> apiRequest() {
        return client.request(HttpMethod.GET, 8080, "localhost", "/delay5s")
                .flatMap(httpClientRequest -> httpClientRequest.send())
                .flatMap(r -> r.body())
                .map(b -> b.toString());
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

        final CompositeFuture resultFuture = CompositeFuture.all(IntStream.rangeClosed(1, parallelCount)
                        .boxed()
                        .map(i -> {
                            Future<String> singleParallelFuture = Future.succeededFuture("");
                            for (int j = 0; j < requestsPerParallel; j++) {
                                singleParallelFuture = singleParallelFuture.flatMap(s -> apiRequest()
                                        .onSuccess((s1) -> {
                                            Assertions.assertEquals("hello", s1);
                                            countRequest(counter, i);
                                        }));
                            }
                            return singleParallelFuture;
                        })
                        .collect(Collectors.toList()))
                .onSuccess((unused) -> {
                    final long duration = (System.currentTimeMillis() - start) / 1000;
                    log.info("请求成功：" + counter + "，耗时s：" + duration);
                });

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        resultFuture.toCompletionStage().toCompletableFuture().get();
    }

}
