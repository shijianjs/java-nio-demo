package com.example.javaniodemo.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.javaniodemo.demo.IoParallelUtil.countRequest;

/**
 * 这里选用了hutool，其实调度逻辑上okhttp、apache http、feign都一样，实现 String apiRequest() 就行
 * <p>
 * 大致花费时间：0.5h
 */

@SpringBootTest
public class JavaFeignBioDemo implements ApiRequest<String> {

    @Autowired
    FeignClientDemo feignClientDemo;

    @Component
    @FeignClient(value = "server", url = "http://localhost:8080")
    public static interface FeignClientDemo {
        @GetMapping("/")
        String root();

        @GetMapping("/delay5s")
        String delay5s();
    }


    @Test
    public void singleTest() {
        String result = apiRequest();
        System.out.println(result);
    }

    public String apiRequest() {
        return feignClientDemo.delay5s();
    }

    @Test
    public void multiTest() {
        int parallelCount = 100;
        int requestsPerParallel = 2;

        final ExecutorService threadPool = Executors.newFixedThreadPool(100);
        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        System.out.println("开始执行");
        IntStream.rangeClosed(1, parallelCount)
                .mapToObj(i -> threadPool.submit(() -> {
                    for (int j = 0; j < requestsPerParallel; j++) {
                        String result = apiRequest();
                        Assertions.assertEquals("hello", result);
                        countRequest(counter, i);
                    }
                }))
                // 这个toList必须存在，否则stream流会顺序执行，就没有并发了
                .collect(Collectors.toList())
                .forEach(future -> {
                    try {
                        future.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        long end = System.currentTimeMillis();
        final long duration = (end - start) / 1000;
        System.out.println("请求成功：" + counter + "，耗时s：" + duration);
    }


}
