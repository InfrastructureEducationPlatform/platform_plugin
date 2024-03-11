package com.example.demo.consumer

import com.example.demo.models.messages.AcceptedDeploymentEvent
import com.example.demo.models.messages.DeploymentResultEvent
import com.example.demo.models.messages.MassTransitMessageWrapper
import com.example.demo.models.messages.StartDeploymentEvent
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.dto.AwsConfiguration
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.service.aws.DBApiService
import com.example.demo.web.service.aws.VMApiService
import com.example.demo.web.service.aws.VpcService
import com.example.demo.web.service.aws.WebApiService
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
class DeploymentStartConsumer(
        private val jacksonObjectMapper: ObjectMapper,
        private val vmApiService: VMApiService,
        private val webApiService: WebApiService,
        private val dbApiService: DBApiService,
        private val vpcService: VpcService,
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

        // Create AWS Credential
        // {"Region": "Default Region Code(i.e: ap-northeast-2)", "AccessKey": "Access Key ID", "SecretKey": "Access Secret Key"}
        val awsCredential = AwsConfiguration(
                region = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["Region"].asText(),
                accessKeyId = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["AccessKey"].asText(),
                secretAccessKey = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["SecretKey"].asText()
        )

        val blockOutputDeferredList = mutableListOf<Deferred<BlockOutput>>()
        runCatching {
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

            val vpc = vpcService.createVpc(awsCredential)

            //Service 배포
            coroutineScope {
                for (block in startDeploymentEvent.sketchProjection.blockSketch.get("blockList")) {
                    val convertedBlock = jacksonObjectMapper.convertValue(block, Block::class.java)
                    val blockOutputDeferred = async {
                        when (block.get("type").asText()) {
                            "virtualMachine" -> vmApiService.createEC2Instance(awsCredential, convertedBlock, "ami-0f3a440bbcff3d043", vpc)
                            "webServer" -> webApiService.createEBInstance(convertedBlock, awsCredential, vpc)
                            "database" -> dbApiService.createDatabaseInstance(block.get("name").asText(), convertedBlock, awsCredential, vpc)
                            else -> {
                                throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                            }
                        }
                    }
                    blockOutputDeferredList.add(blockOutputDeferred)
                }
            }
        }.onSuccess {
            // Send deployment result
            val blockOutputList = blockOutputDeferredList.map { it.await() }
            val deploymentResultEvent = DeploymentResultEvent(startDeploymentEvent.deploymentLogId, blockOutputList, true)
            rabbitTemplate.convertAndSend("deployment.result", "", jacksonObjectMapper.writeValueAsString(deploymentResultEvent.toMassTransitMessageWrapper()))
        }.onFailure {
            logger.error(it) { "Error occurred while deploying" }
            // Send deployment result
            val deploymentResultEvent = DeploymentResultEvent(startDeploymentEvent.deploymentLogId, null, false)
            rabbitTemplate.convertAndSend("deployment.result", "", jacksonObjectMapper.writeValueAsString(deploymentResultEvent.toMassTransitMessageWrapper()))
        }

        return Mono.empty()
    }
}