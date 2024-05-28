package com.blockinfrastructure.plugin.dto.request

import com.blockinfrastructure.plugin.dto.internal.*
import com.blockinfrastructure.plugin.dto.message.DeploymentResultEvent
import com.fasterxml.jackson.annotation.JsonProperty

data class DeploymentOutputRequestDto(
        @JsonProperty("eb_outputs")
        val ebOutputs: List<EbOutput>,
        @JsonProperty("ec2_outputs")
        val ec2Outputs: List<EC2Output>,
        @JsonProperty("rds_outputs")
        val rdsOutputs: List<RdsOutput>,
        @JsonProperty("cache_outputs")
        val cacheOutputs: List<InternalCacheOutput>,
        @JsonProperty("mq_outputs")
        val mqOutputs: List<InternalMqOutput>,
        @JsonProperty("deployment_log_id")
        val deploymentLogId: String,
        @JsonProperty("sketch_id")
        val sketchId: String,
        @JsonProperty("sketch_name")
        val sketchName: String
) {
    fun toDeploymentResultEvent(): DeploymentResultEvent {
        val deploymentId = deploymentLogId
        val blockOutputList = ArrayList<BlockOutput>()
        for (ec2 in ec2Outputs) {
            val blockOutput = BlockOutput(ec2.blockId, "virtualMachine",
                    VirtualMachineOutput(ec2.instanceId, ec2.ipAddress, ec2.sshKey, ec2.instanceTier), null, null, null, null)
            blockOutputList.add(blockOutput)
        }
        for (eb in ebOutputs) {
            val blockOutput = BlockOutput(eb.blockId, "webServer",
                    null, WebServerOutput(eb.appName, eb.fqdn, eb.instanceTier), null, null, null)
            blockOutputList.add(blockOutput)
        }
        for (rds in rdsOutputs) {
            val blockOutput = BlockOutput(rds.blockId, "database",
                    null, null, DatabaseOutput(rds.identifier, rds.fqdn, rds.username, rds.password), null, null)
            blockOutputList.add(blockOutput)
        }

        for (cache in cacheOutputs) {
            val blockOutput = BlockOutput(cache.blockId, "cache",
                    null, null, null, CacheOutput(cache.redisHost, cache.redisPort, cache.redisPrimaryAccessKey,), null
            )
            blockOutputList.add(blockOutput)
        }

        for (mq in mqOutputs) {
            val blockOutput = BlockOutput(mq.blockId, "mq",
                    null, null, null, null, MqOutput(mq.mqAmqps)
            )
            blockOutputList.add(blockOutput)
        }
        return DeploymentResultEvent(deploymentId, blockOutputList, true)

    }
}

data class EC2Output(
        @JsonProperty("block_id")
        val blockId: String,
        @JsonProperty("instance_id")
        val instanceId: String,
        @JsonProperty("ip_address")
        val ipAddress: String,
        @JsonProperty("ssh_key")
        val sshKey: String,
    @JsonProperty("actual_instance_tier")
    val instanceTier: String
)

data class EbOutput(
        @JsonProperty("block_id")
        val blockId: String,
        @JsonProperty("app_name")
        val appName: String,
        @JsonProperty("fqdn")
        val fqdn: String,
        @JsonProperty("actual_instance_tier")
        val instanceTier: String
)


data class RdsOutput(
        @JsonProperty("block_id")
        val blockId: String,
        @JsonProperty("fqdn")
        val fqdn: String,
        @JsonProperty("identifier")
        val identifier: String,
        @JsonProperty("username")
        val username: String,
        @JsonProperty("password")
        val password: String
)

data class InternalCacheOutput(
    @JsonProperty("block_id")
    val blockId: String,
    @JsonProperty("redis_host")
    val redisHost: String,
    @JsonProperty("redis_port")
    val redisPort: Int,
    @JsonProperty("redis_primary_access_key")
    val redisPrimaryAccessKey: String,
)

data class InternalMqOutput(
    @JsonProperty("block_id")
    val blockId: String,
    @JsonProperty("mq_amqps")
    val mqAmqps: String,
)