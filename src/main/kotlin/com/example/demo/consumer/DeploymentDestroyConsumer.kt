package com.example.demo.consumer

import com.example.demo.models.messages.AcceptedDeploymentEvent
import com.example.demo.models.messages.DeploymentResultEvent
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
        private val vpcService: VpcService,
        private val rabbitTemplate: RabbitTemplate
) {
    @RabbitListener(
            ackMode = "MANUAL",
            bindings = [QueueBinding(
                    value = Queue(value = "deployment.destroy.plugin-destroy", durable = "true"),
                    exchange = Exchange(value = "deployment.destroy", type = "fanout"),
            )])
    suspend fun consumeDeploymentStartMessage(message: String): Mono<Unit> {
        val rootMessage = jacksonObjectMapper.readTree(message)

        // Get message object field and convert it to a StartDeploymentEvent object
        val startDeploymentEvent = jacksonObjectMapper.convertValue(rootMessage.get("message"), StartDeploymentEvent::class.java)

        // Create AWS Credential
        // {"Region": "Default Region Code(i.e: ap-northeast-2)", "AccessKey": "Access Key ID", "SecretKey": "Access Secret Key"}
        val awsCredential = AwsConfiguration(
                region = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["Region"].asText(),
                accessKeyId = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["AccessKey"].asText(),
                secretAccessKey = startDeploymentEvent.pluginInstallationProjection.pluginConfiguration["SecretKey"].asText()
        )



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
        val blockOutputDeferredList = mutableListOf<Deferred<Boolean>>()
        coroutineScope {
            for (block in startDeploymentEvent.sketchProjection.blockSketch.get("blockList")) {
                val blockOutputDeferred = async {
                    when (block.get("type").asText()) {
                        "virtualMachine" -> vmApiService.deleteVm(jacksonObjectMapper.convertValue(block, Block::class.java), awsCredential)
                        "webServer" -> webApiService.deleteWebServer(jacksonObjectMapper.convertValue(block, Block::class.java), awsCredential)
                        "database" -> dbApiService.deleteDb(jacksonObjectMapper.convertValue(block, Block::class.java), awsCredential)
                        else -> {
                            throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                        }
                    }
                }
                blockOutputDeferredList.add(blockOutputDeferred)
            }
        }
        val blockOutputList = blockOutputDeferredList.map { it.await() }
        vpcService.deleteVpc(awsCredential)

        return Mono.empty()
    }
}