package com.example.javaniodemo.demo;

import java.util.concurrent.atomic.AtomicInteger;

public class IoParallelUtil {

    public static void countRequest(AtomicInteger counter, int parallelNumber) {
        System.out.println("并发序号："+ parallelNumber +"\t请求计数："+ counter.incrementAndGet()+"\t线程总数："+Thread.activeCount());
    }
}
