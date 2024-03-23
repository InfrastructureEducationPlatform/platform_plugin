package com.blockinfrastructure.plugin.dto.message

import com.blockinfrastructure.plugin.dto.internal.BlockOutput


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