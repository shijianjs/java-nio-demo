package com.example.javaniodemo.demo

import cn.hutool.core.thread.ThreadUtil
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CoroutineNio2Demo：coroutine对接nio2
 *
 * 可以对比下JavaNio2Demo写法，二者的功能一模一样，仅仅是做了kotlin协程的对接，就少了3层回调；
 * apiRequest代码量从79行缩减到29行，就算加上可以复用的工具方法的11行，也就40行；
 * 并且命令式顺序执行，逻辑也清晰了很多
 */
class CoroutineNio2Demo : ApiRequestCoroutine<String> {
    lateinit var client: AsynchronousChannelGroup

    @BeforeEach
    fun setUp() {
        // 限制ForkJoinPool的线程数，否则会创建和cpu核数相同的线程
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2")
        client = singleThreadClient()
    }

    private fun singleThreadClient(): AsynchronousChannelGroup {
        return AsynchronousChannelGroup.withFixedThreadPool(1, ThreadUtil.newNamedThreadFactory("nio2-", true))
    }

    @Test
    fun singleTest(): Unit = runBlocking {
        val result: String = apiRequest()
        log.info(result)
    }


    suspend fun <T> execAsync(callback: (CompletionHandler<T, Unit>) -> Unit): T = suspendCancellableCoroutine {
        callback(object : CompletionHandler<T, Unit> {
            override fun completed(result: T, attachment: Unit) {
                it.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                it.resumeWithException(exc)
            }
        })
    }


    override suspend fun apiRequest(): String {
        val channel = AsynchronousSocketChannel.open(client)
        // 第1层回调，建立连接
        execAsync<Void?> { channel.connect(InetSocketAddress("localhost", 8080), Unit, it) }
        val reqStr = "GET /delay5s HTTP/1.1\n\n"
        val reqStrBytes = reqStr.toByteArray(StandardCharsets.UTF_8)
        val outBuffer = ByteBuffer.wrap(reqStrBytes)
        // 第2层回调，等待写完
        val result = execAsync<Int> { channel.write(outBuffer, Unit, it) }
        Assertions.assertEquals(reqStrBytes.size, result)
        val buffer = ByteBuffer.allocate(256)
        // 第3层回调，等待读完。这里代码处理的不严谨，半包、粘包等都没考虑
        execAsync<Int> { channel.read(buffer, Unit, it) }
        buffer.flip()
        val bytes = ByteArray(buffer.remaining())
        buffer[bytes]
        val responseStr = String(bytes)
        // log.info(responseStr);
        val split = responseStr.split("\r\n\r\n".toRegex(), 2).toTypedArray()
        val header = split[0]
        val body = split[1]
        val bodySize: Int = header.lines()
            .filter { it.startsWith("Content-Length:") }
            .map { s -> s.split(":".toRegex()).toTypedArray()[1].trim().toInt() }
            .first()
        Assertions.assertEquals(bodySize, body.toByteArray().size)
        channel.close()
        return body
    }

    @Test
    fun multiTest() {
        val block: suspend CoroutineScope.() -> Unit = {
            val parallelCount = 100
            val requestsPerParallel = 2

            val counter = AtomicInteger(0)
            val start = System.currentTimeMillis()
            log.info("开始执行")
            (1..parallelCount).map { i ->
                async {
                    repeat(requestsPerParallel) {
                        val result: String = apiRequest()
                        require(result == "hello")
                        IoParallelUtil.countRequest(counter, i)
                    }
                }
            }.awaitAll()
            val end = System.currentTimeMillis()
            val duration = (end - start) / 1000
            log.info("请求成功：$counter，耗时：$duration s")
        }

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        runBlocking(block = block)
    }
    companion object{
        val log = KotlinLogging.logger {  }
    }
}