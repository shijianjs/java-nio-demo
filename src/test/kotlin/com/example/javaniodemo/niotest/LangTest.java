package com.example.javaniodemo.niotest;

import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;

public class LangTest {
    @Test
    public void testQueue() {
        PriorityQueue<Integer> queue = new PriorityQueue<>();
        queue.add(3);
        queue.add(1);
        queue.add(2);
        queue.add(1);
        queue.add(3);
        while (!queue.isEmpty()){
            System.out.println(queue.poll());
        }
    }
}
