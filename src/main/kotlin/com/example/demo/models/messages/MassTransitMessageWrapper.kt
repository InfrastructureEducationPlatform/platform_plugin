package com.example.demo.models.messages


data class MassTransitMessageWrapper<T>(
        val messageType: List<String>,
        val message: T
)