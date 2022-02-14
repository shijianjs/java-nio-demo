package com.example.javaniodemo.niotest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class MonoTest {


    @Test
    public void repeatTest() {
        final Flux<String> abb = Mono.just(1)
                .flatMap(l ->
                        // 暂时没找到让这里顺序执行多次的办法，只能像SpringWebClientNioDemo似的结束拼接回调
                        Mono.delay(Duration.ofSeconds(1)).just("abb"))
                .doOnNext(s -> log.info(s))
                .repeat(3);
        // 阻塞主线程
        abb.blockLast();
    }
}
