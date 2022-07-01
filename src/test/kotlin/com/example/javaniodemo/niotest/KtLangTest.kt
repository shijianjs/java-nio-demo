package com.example.javaniodemo.niotest

import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

class KtLangTest {
    @OptIn(ExperimentalTime::class)
    @Test
    fun `time test`() {
        println(2.minutes.inWholeMilliseconds)
    }
}