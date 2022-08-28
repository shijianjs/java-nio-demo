package com.example.javaniodemo.myfuture;

import cn.hutool.core.lang.Assert;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * - 每一个操作符设置自己的调度逻辑
 * - 每一个操作符不改变上一个操作符，上一个操作符是可以复用的 todo 这个在当前逻辑不行
 * - 每一个非触发操作符，产生新的future
 *
 * @param <T>
 */
public class MyCompletableFuture<T> {


    /**
     * 非阻塞执行、调度逻辑
     */
    private Runnable runnable;

    /**
     * 定时器
     * <p>
     * 延时任务的执行时间点
     */
    private long triggerTime = 0;

    /**
     * 执行结果
     */
    private T result;

    /**
     * 成功状态
     */
    private Boolean success = null;

    /**
     * 下一个节点
     */
    private MyCompletableFuture<?> next = null;

    /**
     * 调用链头部
     */
    private MyCompletableFuture<?> head = null;

    /**
     * 锁，调用future阻塞等待用的
     */
    private MyLatch blockLatch = new MyLatch();
    /**
     * 并行计数器
     * <p>
     * 只在allOf里面用着了
     */
    private int counter = 0;

    /**
     * 外面不能直接new
     */
    private MyCompletableFuture() {
    }

    /**
     * 完成这个future
     * @param result
     */
    public void complete(T result) {
        if (!this.isCompleted){
            this.isCompleted = true;
            this.setSuccess(result);
        }
    }

    /**
     * 暂时不做异常处理
     *
     * @param e
     */
    public void completeExceptionally(Exception e) {
        throw new RuntimeException(e);
    }

    /**
     * 模仿CountDownLatch，阻塞线程用
     */
    public static class MyLatch {

        /**
         * 阻塞
         */
        @SneakyThrows
        public synchronized void block() {
            wait();
        }

        /**
         * 阻塞一定时长
         */
        @SneakyThrows
        public synchronized void block(long delay) {
            wait(delay);
        }

        /**
         * 中断阻塞
         */
        public synchronized void unblock() {
            notifyAll();
        }
    }

    /**
     * 运行时
     */
    private static class MyFutureRuntime {

        /**
         * 实例
         */
        private static final MyFutureRuntime INSTANCE = new MyFutureRuntime();

        /**
         * 调度线程
         */
        private final Thread runtime = new Thread(this::loop, "MyCompletableFutureThread");

        /**
         * 循环线程阻塞等待锁
         */
        private final MyLatch loopLatch = new MyLatch();

        /**
         * 优先级队列
         *
         * 不引用并发包的队列，维持该类的纯净
         *
         * 除了add外，其他操作应该是严格的单线程，不需要加锁，有并发问题再说
         */
        private final PriorityQueue<MyCompletableFuture<?>> queue = new PriorityQueue<>(Comparator.comparing(f -> f.triggerTime));

        /**
         * 构造器
         *
         * 同时启动调度线程
         */
        private MyFutureRuntime() {
            runtime.start();
        }

        /**
         * 事件循环
         */
        @SneakyThrows
        private void loop() {
            while (true) {
                blockThreadToWait();
                while (!isEmpty() && getDelay()<=0) {
                    try {
                        Objects.requireNonNull(poll()).runnable.run();
                    } catch (Exception e) {
                        // 暂时不处理异常
                        e.printStackTrace();
                    }
                }
            }
        }

        @Nullable
        private synchronized MyCompletableFuture<?> poll() {
            return queue.poll();
        }

        /**
         * 避免cpu空转
         *
         * @throws InterruptedException
         */
        private void blockThreadToWait() throws InterruptedException {
            // loopLatch = new CountDownLatch(1);
            if (isEmpty()) {
                loopLatch.block();
            } else {
                final long delay = getDelay();
                if (delay > 0) {
                    loopLatch.block(delay);
                }
            }
        }

        /**
         * 不加这几个同步会遇到并发问题
         */
        private synchronized boolean isEmpty(){
            return queue.isEmpty();
        }
        /**
         * 不加这几个同步会遇到并发问题
         */
        private synchronized long getDelay(){
            if (queue.isEmpty()){
                return 1;
            }
            return queue.peek().triggerTime - System.currentTimeMillis();
        }

        /**
         * 添加任务
         * <p>
         * 有可能是非调度线程在调用这个方法，所以每次添加完通知调度线程该循环了
         */
        private synchronized void addTask(MyCompletableFuture<?> task) {
            queue.add(task);
            loopLatch.unblock();
        }
    }


    /**
     * 并行执行全部，全部成功后返回
     */
    public static MyCompletableFuture<Void> newAllOf(MyCompletableFuture<?>... cfs) {
        final MyCompletableFuture<Void> allFuture = new MyCompletableFuture<>();
        allFuture.counter = cfs.length;
        allFuture.runnable = () -> {
            Arrays.stream(cfs).forEach(childFuture -> {
                childFuture.whenComplete((n, e) -> {
                    allFuture.counter--;
                    if (allFuture.counter <= 0) {
                        allFuture.setSuccess(null);
                    }
                });
                childFuture.fire();
            });
        };
        allFuture.head = allFuture;
        return allFuture;
    }

    /**
     * 构造器
     * <p>
     * MyCompletableFuture当前唯一的入口
     */
    public static <U> MyCompletableFuture<U> newWithValue(U value) {
        final MyCompletableFuture<U> future = new MyCompletableFuture<>();
        future.success = true;
        future.result = value;
        future.runnable = () -> {
            future.setSuccess(value);
        };
        future.head = future;
        return future;
    }

    public static <U> MyCompletableFuture<U> newToComplete() {
        final MyCompletableFuture<U> future = new MyCompletableFuture<>();
        future.isCompleted = false;
        // future.success = true;
        future.runnable = () -> {
            // future.setSuccess(value);
        };
        future.head = future;
        return future;
    }


    /**
     * 典型的异步回调
     */
    public <U> MyCompletableFuture<U> thenCompose(Function<? super T, ? extends MyCompletableFuture<U>> fn) {
        final MyCompletableFuture<U> nextFuture = new MyCompletableFuture<>();
        nextFuture.runnable = () -> {
            final MyCompletableFuture<U> callbackFuture = fn.apply(this.result);
            callbackFuture.whenComplete((u, e) -> {
                nextFuture.setSuccess(u);
            });
            callbackFuture.fire();
        };
        setNext(nextFuture);
        return nextFuture;
    }

    /**
     * 类似执行完成后置事件
     */
    public MyCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        final MyCompletableFuture<T> nextFuture = new MyCompletableFuture<>();
        nextFuture.runnable = () -> {
            action.accept(this.result, null);
            nextFuture.setSuccess(this.result);
        };
        setNext(nextFuture);
        return nextFuture;
    }

    /**
     * 设置下一个执行的future
     * <p>
     * 同时传递头部
     */
    private void setNext(MyCompletableFuture<?> nextFuture) {
        this.next = nextFuture;
        nextFuture.head = this.head;
    }

    /**
     * 延时任务
     */
    public MyCompletableFuture<T> delay(long delayMillis) {
        Assert.isTrue(delayMillis > 0);
        final MyCompletableFuture<T> delayFuture = new MyCompletableFuture<>();
        delayFuture.triggerTime = delayMillis + System.currentTimeMillis();
        delayFuture.runnable = () -> {
            delayFuture.setSuccess(this.result);
        };
        setNext(delayFuture);
        return delayFuture;
    }

    /**
     * 触发执行，但不阻塞当前线程
     */
    public MyCompletableFuture<T> fire() {
        toLoop(this.head);
        return this;
    }

    /**
     * 触发执行，阻塞当前线程，等待结果
     */
    public synchronized T block() {
        if (isSuccess()) {
            return result;
        }
        this.fire();

        // 线程阻塞等待结果
        blockLatch.block();
        return result;
    }

    /**
     * 是否执行成功
     */
    public boolean isSuccess() {
        return success != null && success;
    }

    /**
     * 执行成功
     */
    private void setSuccess(T result) {
        // 设置成功结果
        this.result = result;
        this.success = true;

        // 执行下一条
        if (next != null && isSubscribed) {
            toLoop(next);
        }

        // 通知阻塞等待的线程
        blockLatch.unblock();
    }

    /**
     * 推入到事件循环，开始执行
     * <p>
     * 这里只支持在设定好的线程执行，不支持在当前线程执行
     */
    private static void toLoop(MyCompletableFuture<?> future) {
        future.isSubscribed = true;
        if (future.isCompleted) {
            MyFutureRuntime.INSTANCE.addTask(future);
        }
    }

    /**
     * 是否完成
     *
     * 用于判断未初始化值的future
     */
    boolean isCompleted = true;

    boolean isSubscribed = false;
}
