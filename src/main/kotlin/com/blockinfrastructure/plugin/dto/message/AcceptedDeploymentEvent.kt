package com.blockinfrastructure.plugin.dto.message

data class AcceptedDeploymentEvent(
        val deploymentId: String
) {
    fun toMassTransitMessageWrapper(): MassTransitMessageWrapper<AcceptedDeploymentEvent> {
        return MassTransitMessageWrapper(
                messageType = listOf("urn:message:BlockInfrastructure.Common.Models.Messages:DeploymentAcceptedEvent"),
                message = this
        )
    }
}