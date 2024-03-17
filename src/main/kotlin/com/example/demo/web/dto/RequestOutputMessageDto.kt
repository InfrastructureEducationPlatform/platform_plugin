package com.example.demo.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class RequestOutputMessageDto (
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
)

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