package com.example.javaniodemo.demo;

import java.util.concurrent.atomic.AtomicInteger;

@lombok.extern.slf4j.Slf4j
public class IoParallelUtil {

    public static void countRequest(AtomicInteger counter, int parallelNumber) {
        log.info("并发序号："+ parallelNumber +"\t请求计数："+ counter.incrementAndGet()+"\t线程总数："+Thread.activeCount());
    }
}
