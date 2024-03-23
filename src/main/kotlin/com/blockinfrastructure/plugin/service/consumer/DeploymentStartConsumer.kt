package com.blockinfrastructure.plugin.service.consumer

import com.blockinfrastructure.plugin.client.GithubFeignClient
import com.blockinfrastructure.plugin.dto.message.AcceptedDeploymentEvent
import com.blockinfrastructure.plugin.dto.message.DeploymentResultEvent
import com.blockinfrastructure.plugin.dto.message.StartDeploymentEvent
import com.blockinfrastructure.plugin.dto.request.DispatchGithubActionRequestDto
import com.blockinfrastructure.plugin.dto.request.EventType
import com.blockinfrastructure.plugin.legacy.dto.RequestSketchDto
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DeploymentStartConsumer(
        private val jacksonObjectMapper: ObjectMapper,
        private val githubFeignClient: GithubFeignClient,
        private val rabbitTemplate: RabbitTemplate
) {
    val logger = KotlinLogging.logger {}

    @RabbitListener(
            ackMode = "MANUAL",
            bindings = [QueueBinding(
                    value = Queue(value = "deployment.started.plugin-start", durable = "true"),
                    exchange = Exchange(value = "deployment.started", type = "fanout"),
            )])
    suspend fun consumeDeploymentStartMessage(message: String): Mono<Unit> {
        val rootMessage = jacksonObjectMapper.readTree(message)

        // Get message object field and convert it to a StartDeploymentEvent object
        val startDeploymentEvent = jacksonObjectMapper.convertValue(rootMessage.get("message"), StartDeploymentEvent::class.java)

        // Send accepted deployment event
        val acceptedDeploymentEvent = AcceptedDeploymentEvent(startDeploymentEvent.deploymentLogId)
        rabbitTemplate.convertAndSend("deployment.accepted", "", jacksonObjectMapper.writeValueAsString(acceptedDeploymentEvent.toMassTransitMessageWrapper()))

        runCatching {
            // Service 가능 여부 체크
            val blockList = startDeploymentEvent.getBlockList(jacksonObjectMapper)

            // Service 배포
            val sketchDto = RequestSketchDto(
                    sketchId = startDeploymentEvent.sketchProjection.blockSketch.get("sketchId").asText(),
                    blockList = blockList,
                    pluginInstallationInformation = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration
            )
            githubFeignClient.dispatch(DispatchGithubActionRequestDto.fromRequestSketchDto(EventType.DEPLOY_INFRASTRUCTURE, startDeploymentEvent.deploymentLogId, sketchDto))
        }.onSuccess {
            // Send deployment result
            logger.info { "Successfully dispatched to github action." }
        }.onFailure {
            logger.error(it) { "Error occurred while deploying" }
            // Send deployment result
            val deploymentResultEvent = DeploymentResultEvent(startDeploymentEvent.deploymentLogId, null, false)
            rabbitTemplate.convertAndSend("deployment.result", "", jacksonObjectMapper.writeValueAsString(deploymentResultEvent.toMassTransitMessageWrapper()))
        }

        return Mono.empty()
    }
}