package com.blockinfrastructure.plugin.dto.message


data class MassTransitMessageWrapper<T>(
        val messageType: List<String>,
        val message: T
)