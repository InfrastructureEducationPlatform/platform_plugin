package com.blockinfrastructure.plugin.dto.request

import com.blockinfrastructure.plugin.legacy.dto.RequestSketchDto
import com.blockinfrastructure.plugin.legacy.dto.TfVar
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class DispatchGithubActionRequestDto(
    @JsonProperty("event_type")
    val eventType: String,
    @JsonProperty("client_payload")
    val clientPayload: ClientPayload
) {
    companion object {
        fun fromRequestSketchDto(
            eventType: EventType,
            deploymentId: String,
            requestSketchDto: RequestSketchDto,
            pluginId: String
        ): DispatchGithubActionRequestDto {
            val test = DispatchGithubActionRequestDto(
                eventType.eventName,
                requestConverter(deploymentId, requestSketchDto, pluginId)
            )
            println(jacksonObjectMapper().writeValueAsString(test))
            return test
        }

        private fun requestConverter(deploymentId: String, request: RequestSketchDto, pluginId: String): ClientPayload {
            var ec2Defs = ""
            var ebDefs = ""
            var rdsDefs = ""
            var cacheDefs = ""
            var mqDefs = ""

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
                                container_port  = "${block.webServerFeatures!!.containerMetadata.containerPort}"
                            },
                            db_ref = "${request.blockList.find { it.type == "database" && it.id == block.webServerFeatures!!.connectionMetadata.dbRef }?.name ?: ""}"
                            cache_ref = "${request.blockList.find { it.type == "cache" && it.id == block.webServerFeatures!!.connectionMetadata.cacheRef }?.name ?: ""}"
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

                    "cache" -> {
                        val cacheDef = """
                        {
                            block_id             = "${block.id}",
                            name                 = "${block.name}",
                            tier                 = "${block.cacheFeatures!!.tier}"
                        },
                    """.trimIndent()
                        cacheDefs += cacheDef
                    }

                    "mq" -> {
                        val mqDef = """
                        {
                            block_id             = "${block.id}",
                            name                 = "${block.name}",
                            tier                 = "${block.mqFeatures!!.tier}"
                            region               = "${block.mqFeatures!!.region}"
                            username             = "${block.mqFeatures!!.username}"
                            password             = "${block.mqFeatures!!.password}"
                        },
                    """.trimIndent()
                        mqDefs += mqDef
                    }

                    else -> {}
                }
            }

            val tfvar = TfVar(
                deploymentId,
                request.sketchId,
                "sketch_name",
                request.pluginInstallationInformation["Region"].asText(),
                ec2Defs, ebDefs, rdsDefs, cacheDefs, mqDefs
            )

            val authStr = when (pluginId) {
                "aws-static" -> "access_key = \"${request.pluginInstallationInformation["AccessKey"].asText()}\"\n" +
                        "secret_key = \"${request.pluginInstallationInformation["SecretKey"].asText()}\""

                "azure-static" -> "client_id       = \"${request.pluginInstallationInformation["ClientId"].asText()}\"\n" +
                        "client_secret   = \"${request.pluginInstallationInformation["ClientSecret"].asText()}\"\n" +
                        "subscription_id = \"${request.pluginInstallationInformation["SubscriptionId"].asText()}\"\n" +
                        "tenant_id       = \"${request.pluginInstallationInformation["TenantId"].asText()}\""

                else -> ""
            }

            val tfvarStr = tfvarToStr(tfvar, authStr)

            return ClientPayload(request.sketchId, tfvarStr)
        }

        private fun tfvarToStr(tfVar: TfVar, authStr: String): String {
            val retStr = """
            deployment_log_id = "${tfVar.deploymentId}"
            sketch_id         = "${tfVar.sketchId}"
            sketch_name       = "${tfVar.sketchName}"
            
            # General
            region     = "${tfVar.region}"
            $authStr
            
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
            
            # Cache
            cache_def = [
                ${tfVar.cacheDef}
            ]
            
            # MQ
            mq_def = [
                ${tfVar.mqDef}
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