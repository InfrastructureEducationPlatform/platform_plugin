package com.blockinfrastructure.plugin.legacy.service.aws

import com.blockinfrastructure.plugin.client.GithubFeignClient
import com.blockinfrastructure.plugin.dto.request.ClientPayload
import com.blockinfrastructure.plugin.dto.request.DispatchGithubActionRequestDto
import com.blockinfrastructure.plugin.legacy.CustomException
import com.blockinfrastructure.plugin.legacy.ErrorCode
import com.blockinfrastructure.plugin.legacy.dto.RequestSketchDto
import com.blockinfrastructure.plugin.legacy.dto.TfVar
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
    fun dispatchGithubAction(eventType: String, deploymentId: String, sketch: RequestSketchDto) {
        val clientPayload = requestConverter(deploymentId, sketch)
        val dispatchGithubActionRequestDto = DispatchGithubActionRequestDto(eventType, clientPayload)
        githubFeignClient.dispatch(dispatchGithubActionRequestDto)
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
}