package com.blockinfrastructure.plugin.dto.request

import com.blockinfrastructure.plugin.dto.internal.BlockOutput
import com.blockinfrastructure.plugin.dto.internal.DatabaseOutput
import com.blockinfrastructure.plugin.dto.internal.VirtualMachineOutput
import com.blockinfrastructure.plugin.dto.internal.WebServerOutput
import com.blockinfrastructure.plugin.dto.message.DeploymentResultEvent
import com.fasterxml.jackson.annotation.JsonProperty

data class DeploymentOutputRequestDto(
        @JsonProperty("eb_outputs")
        val ebOutputs: List<EbOutput>,
        @JsonProperty("ec2_outputs")
        val ec2Outputs: List<EC2Output>,
        @JsonProperty("rds_outputs")
        val rdsOutputs: List<RdsOutput>,
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
                    VirtualMachineOutput(ec2.instanceId, ec2.ipAddress, ec2.sshKey), null, null)
            blockOutputList.add(blockOutput)
        }
        for (eb in ebOutputs) {
            val blockOutput = BlockOutput(eb.blockId, "webServer",
                    null, WebServerOutput(eb.appName, eb.fqdn), null)
            blockOutputList.add(blockOutput)
        }
        for (rds in rdsOutputs) {
            val blockOutput = BlockOutput(rds.blockId, "database",
                    null, null, DatabaseOutput(rds.identifier, rds.fqdn, rds.username, rds.password))
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
        val sshKey: String
)

data class EbOutput(
        @JsonProperty("block_id")
        val blockId: String,
        @JsonProperty("app_name")
        val appName: String,
        @JsonProperty("fqdn")
        val fqdn: String
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