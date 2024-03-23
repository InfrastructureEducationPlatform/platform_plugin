package com.example.demo.consumer

import com.example.demo.models.messages.AcceptedDeploymentEvent
import com.example.demo.models.messages.DeploymentResultEvent
import com.example.demo.models.messages.StartDeploymentEvent
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.dto.AwsConfiguration
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.RequestSketchDto
import com.example.demo.web.service.aws.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DeploymentDestroyConsumer(
        private val jacksonObjectMapper: ObjectMapper,
        private val vmApiService: VMApiService,
        private val webApiService: WebApiService,
        private val dbApiService: DBApiService,
        private val githubFeignService: GithubFeignService,
) {
    val logger = KotlinLogging.logger {}

    @RabbitListener(
            ackMode = "MANUAL",
            bindings = [QueueBinding(
                    value = Queue(value = "deployment.destroy.plugin-destroy", durable = "true"),
                    exchange = Exchange(value = "deployment.destroy", type = "fanout"),
            )])
    suspend fun consumeDeploymentStartMessage(message: String): Mono<Unit> {
        runCatching {
            val rootMessage = jacksonObjectMapper.readTree(message)

            // Get message object field and convert it to a StartDeploymentEvent object
            val startDeploymentEvent = jacksonObjectMapper.convertValue(rootMessage.get("message"), StartDeploymentEvent::class.java)

            //Service 가능 여부 체크
            startDeploymentEvent.sketchProjection.blockSketch.get("blockList").forEach {
                val block = jacksonObjectMapper.convertValue(it, Block::class.java)
                when (it.get("type").asText()) {
                    "virtualMachine" -> vmApiService.isValidVmBlock(block)
                    "webServer" -> webApiService.isValidWebBlock(block)
                    "database" -> dbApiService.isValidDbBlock(block)
                    else -> {
                        throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                    }
                }
            }

            //배포 삭제
            val sketchDto = RequestSketchDto(
                    sketchId = startDeploymentEvent.sketchProjection.blockSketch.get("sketchId").asText(),
                    blockList = startDeploymentEvent.sketchProjection.blockSketch.get("blockList").map {
                        jacksonObjectMapper.convertValue(it, Block::class.java)
                    },
                    pluginInstallationInformation = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration
            )
            githubFeignService.dispatchGithubAction("destroy-infrastructure", startDeploymentEvent.deploymentLogId, sketchDto)
        }.recoverCatching {
            logger.error { "DeploymentDestroyConsumer Error: ${it.message}" }
        }

        return Mono.empty()
    }
}