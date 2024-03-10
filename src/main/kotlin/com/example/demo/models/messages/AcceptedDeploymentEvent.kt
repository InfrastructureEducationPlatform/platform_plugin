package com.example.demo.models.messages

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