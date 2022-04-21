package com.example.javaniodemo.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
@lombok.extern.slf4j.Slf4j
public class JavaBioDemo implements ApiRequest<String> {
    @Test
    public void singleTest() {
        String result = apiRequest();
        log.info(result);
    }

    public String apiRequest() {
        try (
                final Socket socket = new Socket("localhost", 8080);
                final InputStream inputStream = socket.getInputStream();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                final OutputStream outputStream = socket.getOutputStream();
        ) {
            // outputStream.write(("GET / HTTP/1.1\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write("GET /delay5s HTTP/1.1\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            int length=-1;
            String line=" ";
            while (line.length()>0){
                line = reader.readLine();
                // log.info(line);
                if (line.startsWith("Content-Length:")){
                    length = Integer.parseInt(line.split(":")[1].trim());
                }
            }
            // 这里的实现有点问题，Buffered之后内部的InputStream就读不出来了，Reader又不支持byte[]，
            // 然而length是byte[]的length，不是char[]的length，这个示例里面倒是无所谓了
            final char[] chars = new char[length];
            reader.read(chars,0,length);
            return new String(chars);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void multiTest() {
        int parallelCount = 100;
        int requestsPerParallel = 2;

        final ExecutorService threadPool = Executors.newFixedThreadPool(100);
        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        log.info("开始执行");
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
        log.info("请求成功：" + counter + "，耗时s：" + duration);
    }


}
