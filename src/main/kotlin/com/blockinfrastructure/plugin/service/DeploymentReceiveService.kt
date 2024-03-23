package com.blockinfrastructure.plugin.service

import com.blockinfrastructure.plugin.dto.request.DeploymentOutputRequestDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class DeploymentReceiveService(
        private val rabbitTemplate: RabbitTemplate,
        private val jacksonObjectMapper: ObjectMapper
) {
    fun sendDeploymentEventOutput(request: DeploymentOutputRequestDto) {
        val deploymentResultEvent = request
                .toDeploymentResultEvent()
                .toMassTransitMessageWrapper()
        rabbitTemplate.convertAndSend(
                "deployment.result",
                "",
                jacksonObjectMapper.writeValueAsString(deploymentResultEvent)
        )
    }
}