package com.example.javaniodemo.demo

import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.client.reactive.ReactorResourceFactory
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.LoopResources
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * 大致花费时间：0.5h
 */
class CoroutineMonoNioDemo : ApiRequestCoroutine<String> {
    lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        webClient = singleThreadClient()
    }

    private fun singleThreadClient(): WebClient {
        val reactorResourceFactory = ReactorResourceFactory()
        reactorResourceFactory.isUseGlobalResources = false
        val eventLoopGroup = NioEventLoopGroup(1)
        reactorResourceFactory.loopResources = LoopResources { b: Boolean -> eventLoopGroup }
        reactorResourceFactory.afterPropertiesSet()
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(reactorResourceFactory) { it })
            .build()
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
        webClient.get()
            .uri("http://localhost:8080/delay5s")
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()

}