package org.example.demo.loom.loomtest;

import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.example.demo.loom.loomtest.IoParallelUtil.countRequest;

/**
 *
 * 这里选用了hutool，其实调度逻辑上okhttp、apache http、feign都一样，实现 String apiRequest() 就行
 *
 * 大致花费时间：0.5h
 */
public class LoomHutoolBioDemo implements ApiRequest<String>{
    @Test
    public void singleTest() {
        String result = apiRequest();
        System.out.println(result);
    }

    public String apiRequest() {
        return HttpUtil.get("http://localhost:8080/delay5s");
    }

    @Test
    public void multiTest() {
        int parallelCount = 900;
        int requestsPerParallel = 2;

        final ExecutorService threadPool = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        System.out.println("开始执行");
        IntStream.rangeClosed(1, parallelCount)
                .mapToObj(i -> threadPool.submit(() -> {
                    for (int j = 0; j < requestsPerParallel; j++) {
                        String result = apiRequest();
                        Assertions.assertEquals("hello",result);
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
