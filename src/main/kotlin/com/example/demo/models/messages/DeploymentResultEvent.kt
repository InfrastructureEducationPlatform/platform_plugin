package com.example.demo.models.messages

import com.example.demo.web.dto.BlockOutput

data class DeploymentResultEvent(
        val deploymentId: String,
        val deploymentOutputList: List<BlockOutput>?,
        val isSuccess: Boolean
) {
    fun toMassTransitMessageWrapper(): MassTransitMessageWrapper<DeploymentResultEvent> {
        return MassTransitMessageWrapper(
                messageType = listOf("urn:message:BlockInfrastructure.Common.Models.Messages:DeploymentResultEvent"),
                message = this
        )
    }
}