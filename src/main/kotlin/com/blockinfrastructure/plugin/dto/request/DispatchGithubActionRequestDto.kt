package com.blockinfrastructure.plugin.dto.request

import com.blockinfrastructure.plugin.legacy.dto.RequestSketchDto
import com.blockinfrastructure.plugin.legacy.dto.TfVar
import com.fasterxml.jackson.annotation.JsonProperty

data class DispatchGithubActionRequestDto(
        @JsonProperty("event_type")
        val eventType: String,
        @JsonProperty("client_payload")
        val clientPayload: ClientPayload
) {
    companion object {
        fun fromRequestSketchDto(eventType: EventType, deploymentId: String, requestSketchDto: RequestSketchDto): DispatchGithubActionRequestDto {
            return DispatchGithubActionRequestDto(eventType.eventName, requestConverter(deploymentId, requestSketchDto))
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

                    else -> {}
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
}

data class ClientPayload(
        @JsonProperty("sketch_id")
        val sketchId: String,
        @JsonProperty("tfvars_content")
        val content: String
)

enum class EventType(val eventName: String) {
    DEPLOY_INFRASTRUCTURE("deploy-infrastructure"),
    DESTROY_INFRASTRUCTURE("destroy-infrastructure"),
}