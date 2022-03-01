package org.example.demo.loom.loomtest;

import java.util.concurrent.atomic.AtomicInteger;

public class IoParallelUtil {

    public static void countRequest(AtomicInteger counter, int parallelNumber) {
        System.out.println("parallelNumber="+ parallelNumber +"\tcounter: "+ counter.incrementAndGet()+"\tThreadCount: "+Thread.activeCount());
    }
}
