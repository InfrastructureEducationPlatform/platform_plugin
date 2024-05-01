package com.blockinfrastructure.plugin.service

import com.blockinfrastructure.plugin.dto.message.DeploymentResultEvent
import com.blockinfrastructure.plugin.dto.request.DeploymentOutputRequestDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class DeploymentReceiveService(
    private val rabbitTemplate: RabbitTemplate,
    private val jacksonObjectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    fun sendDeploymentEventOutput(request: DeploymentOutputRequestDto) {
        logger.info("sendDeploymentEventOutput: $request")
        val deploymentResultEvent = request
            .toDeploymentResultEvent()
            .toMassTransitMessageWrapper()
        rabbitTemplate.convertAndSend(
            "deployment.result",
            "",
            jacksonObjectMapper.writeValueAsString(deploymentResultEvent)
        )
    }

    fun sendDeploymentFailedEvent(deploymentLogId: String) {
        rabbitTemplate.convertAndSend(
            "deployment.result",
            "",
            jacksonObjectMapper.writeValueAsString(
                DeploymentResultEvent(
                    deploymentId = deploymentLogId,
                    deploymentOutputList = null,
                    isSuccess = false
                ).toMassTransitMessageWrapper()
            )
        )
    }
}