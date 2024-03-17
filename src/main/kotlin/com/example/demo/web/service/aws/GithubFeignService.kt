package com.example.demo.web.service.aws

import com.example.demo.models.messages.DeploymentResultEvent
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.client.GithubFeignClient
import com.example.demo.web.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


@Service
class GithubFeignService(
    @Autowired private val githubFeignClient: GithubFeignClient,
    private val rabbitTemplate: RabbitTemplate,
    private val jacksonObjectMapper: ObjectMapper
) {
    fun dispatchGithubAction(eventType: String, deploymentId: String, sketch: RequestSketchDto)  {
        val clientPayload = requestConverter(deploymentId, sketch)
        val requestGithubActionDto = RequestGithubActionDto(eventType, clientPayload)
        githubFeignClient.dispatch(requestGithubActionDto)
    }

    private fun requestConverter(deploymentId: String, request: RequestSketchDto): ClientPayload {
        var ec2Defs = ""
        var ebDefs = ""
        var rdsDefs = ""

        for (block in request.blockList) {
            when (block.type) {
                "virtualMachine" -> {
                    val ec2Def = """
                        {
                            block_id = "${block.id}",
                            name     = "${block.name}",
                            ami      = "ami-0f3a440bbcff3d043",
                            tier     = "${block.virtualMachineFeatures!!.tier}"
                        },
                    """.trimIndent()
                    ec2Defs += ec2Def
                }
                "webServer" -> {
                    val appName = block.name
                    val envName =
                        if (appName.length < 36) "$appName-env"
                        else appName.substring(0 until 36) + "-env"
                    val ebDef = """
                        {
                            block_id            = "${block.id}",
                            app_name            = "$appName",
                            env_name            = "$envName",
                            tier                = "${block.webServerFeatures!!.tier}"
                            container_meta_data = {
                                image_tags      = "${block.webServerFeatures!!.containerMetadata.imageTags}"
                                registry_url    = "${block.webServerFeatures!!.containerMetadata.registryUrl}"
                            }
                        },
                    """.trimIndent()
                    ebDefs += ebDef
                }
                "database" -> {
                    val rdsDef = """
                        {
                            block_id             = "${block.id}",
                            name                 = "${block.name}",
                            tier                 = "${block.databaseFeatures!!.tier}"
                            master_user_name     = "${block.databaseFeatures!!.masterUsername}"
                            master_user_password = "${block.databaseFeatures!!.masterUserPassword}"
                        },
                    """.trimIndent()
                    rdsDefs += rdsDef
                }
                else -> {
                    throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                }
            }
        }

        val tfvar = TfVar(
            deploymentId,
            request.sketchId,
            "sketch_name",
            request.pluginInstallationInformation["AccessKey"].asText(),
            request.pluginInstallationInformation["SecretKey"].asText(),
            ec2Defs, ebDefs, rdsDefs
        )

        val tfvarStr = tfvarToStr(tfvar)

        return ClientPayload(request.sketchId, tfvarStr)
    }

    private fun tfvarToStr(tfVar: TfVar): String {
        val retStr = """
            deployment_log_id = "${tfVar.deploymentId}"
            sketch_id         = "${tfVar.sketchId}"
            sketch_name       = "${tfVar.sketchName}"
            
            # General
            access_key = "${tfVar.accessKey}"
            secret_key = "${tfVar.secretKey}"
            
            # EC2
            ec2_def = [
                ${tfVar.ec2Def}
            ]
            
            # EB
            eb_def = [
                ${tfVar.ebDef}
            ]
            
            # RDS
            rds_def = [
                ${tfVar.rdsDef}
            ]
            
        """.trimIndent()

        return retStr
    }

    fun sendOutputMessage(request: RequestOutputMessageDto) {
        val deploymentResultEvent = outputMessageConverter(request)
        rabbitTemplate.convertAndSend("deployment.result", "", jacksonObjectMapper.writeValueAsString(deploymentResultEvent.toMassTransitMessageWrapper()))
    }

    fun outputMessageConverter(response: RequestOutputMessageDto): DeploymentResultEvent {
        val deploymentId = response.deploymentLogId
        val blockOutputList = ArrayList<BlockOutput>()
        for(ec2 in response.ec2Outputs) {
            val blockOutput = BlockOutput(ec2.blockId, "virtualMachine",
                VirtualMachineOutput(ec2.instanceId, ec2.ipAddress, ec2.sshKey), null, null)
            blockOutputList.add(blockOutput)
        }
        for(eb in response.ebOutputs) {
            val blockOutput = BlockOutput(eb.blockId, "webServer",
                null, WebServerOutput(eb.appName, eb.fqdn), null)
            blockOutputList.add(blockOutput)
        }
        for(rds in response.rdsOutputs) {
            val blockOutput = BlockOutput(rds.blockId, "database",
                null, null, DatabaseOutput(rds.identifier, rds.fqdn, rds.username, rds.password))
            blockOutputList.add(blockOutput)
        }
        return DeploymentResultEvent(deploymentId, blockOutputList, true)
    }
}