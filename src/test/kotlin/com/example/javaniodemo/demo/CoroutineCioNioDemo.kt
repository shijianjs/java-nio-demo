package com.example.javaniodemo.demo

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * 可以对比下java bio的写法，几乎一模一样；
 * 也就是说kt下cio可以用bio的写法写nio
 *
 * https://ktor.io/docs/servers-raw-sockets.html
 *
 */
class CoroutineCioNioDemo :ApiRequestCoroutine<String>{
    lateinit var client: TcpSocketBuilder
    @BeforeEach
    internal fun setUp() {
        client = singleThreadClient()
    }

    private fun singleThreadClient() =
        aSocket(ActorSelectorManager(Executors.newSingleThreadExecutor().asCoroutineDispatcher())).tcp()

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

    /**
     * 可以对比下java bio的写法，几乎一模一样；
     * 也就是说kt下cio可以用bio的写法写nio
     */
    override suspend fun apiRequest(): String {
        val socket: Socket = client.connect(InetSocketAddress("127.0.0.1", 8080))
        try {
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)
            output.writeStringUtf8("GET /delay5s HTTP/1.1\n\n")
            var length = -1
            var line = " "
            while (line.isNotEmpty()) {
                line = requireNotNull(input.readUTF8Line())
                // System.out.println(line);
                if (line.startsWith("Content-Length:")) {
                    length = line.split(":".toRegex()).toTypedArray()[1].trim { it <= ' ' }.toInt()
                }
            }
            val bodyBytes = ByteArray(length)
            input.readFully(bodyBytes,0,length)
            return bodyBytes.decodeToString()
        }finally {
            socket.dispose()
        }
    }

}