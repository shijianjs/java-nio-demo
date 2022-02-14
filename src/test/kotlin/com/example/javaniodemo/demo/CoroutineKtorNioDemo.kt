package com.example.javaniodemo.demo

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * 大致花费时间：0.5h
 */
class CoroutineKtorNioDemo :ApiRequestCoroutine<String>{
    lateinit var client: HttpClient
    @BeforeEach
    internal fun setUp() {
        client = singleThreadClient()
    }

    private fun singleThreadClient() = HttpClient(CIO) {
        engine {
            threadsCount = 1
        }
    }

    @Test
    fun singleTest(): Unit = runBlocking {
        val result: String = apiRequest()
        println(result)
    }


    @Test
    fun multiTest(): Unit {
        val block: suspend CoroutineScope.() -> Unit = {
            val parallelCount = 100
            val requestsPerParallel = 2

            val counter = AtomicInteger(0)
            val start = System.currentTimeMillis()
            println("开始执行")
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
            println("请求成功：$counter，耗时：$duration s")
        }

        // 阻塞主线程到运行结束，实际服务端项目中不应该出现这个
        runBlocking(block = block)
    }

    override suspend fun apiRequest(): String =
        client.get("http://localhost:8080/delay5s")

}