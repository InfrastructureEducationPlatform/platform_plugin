package com.blockinfrastructure.plugin.legacy.dto

import com.blockinfrastructure.plugin.dto.internal.ContainerMetadata


class TfVar(
        val deploymentId: String,
        val sketchId: String,
        val sketchName: String,

        val accessKey: String,
        val secretKey: String,

        val ec2Def: String,
        val ebDef: String,
        val rdsDef: String
)


data class Ec2Def(
        val blockId: String,
        val name: String,
        val ami: String,
        val tier: String
)

data class EbDef(
        val blockId: String,
        val appName: String,
        val envName: String,
        val tier: String,
        val containerMetaData: ContainerMetadata
)

data class RdsDef(
        val blockId: String,
        val name: String,
        val tier: String,
        val masterUserName: String,
        val masterUserPassword: String
)