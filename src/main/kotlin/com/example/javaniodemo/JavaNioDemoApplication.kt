package com.example.javaniodemo

import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@SpringBootApplication
class JavaNioDemoApplication

val log = KotlinLogging.logger { }
fun main(args: Array<String>) {
    runApplication<JavaNioDemoApplication>(*args)
}

@RestController
@EnableFeignClients
class ServerController(){

    @RequestMapping("/")
    suspend fun server(): String {
        return "hello"
    }

    @OptIn(ExperimentalTime::class)
    @RequestMapping("/delay5s")
    suspend fun delay5s(): String {
        delay(5.seconds)
        return "hello"
    }

    @RequestMapping("/headerTest")
    suspend fun headerTest(req: ServerHttpRequest): Map<String, MutableList<String>> {
        log.info { req.headers }
        return req.headers.toMap()
    }
}


@Configuration
class Config {
    @Bean
    fun httpMessageConverters(): HttpMessageConverters {
        return HttpMessageConverters()
    }
}