package com.blockinfrastructure.plugin.legacy.dto

data class CreateVpcDto(
        val vpcId: String,
        val cidrBlock: String,
        val subnetIds: List<String>,
        val securityGroupIds: List<String>
)