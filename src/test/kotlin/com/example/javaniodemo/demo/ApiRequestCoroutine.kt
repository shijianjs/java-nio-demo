package com.example.javaniodemo.demo

interface ApiRequestCoroutine<T> {
    suspend fun apiRequest(): T
}