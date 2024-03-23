package com.blockinfrastructure.plugin.dto.message

import com.blockinfrastructure.plugin.dto.internal.Block
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

data class StartDeploymentEvent(
        val deploymentLogId: String,
        val sketchProjection: SketchBlockProjection,
        val pluginInstallationProjection: PluginInstallationProjection
) {
    fun getBlockList(jacksonObjectMapper: ObjectMapper): List<Block> = sketchProjection.blockSketch.get("blockList").map {
        jacksonObjectMapper.convertValue(it, Block::class.java).also { block ->
            if (!block.isValidBlock()) {
                throw RuntimeException("Invalid block, block data: ${jacksonObjectMapper.writeValueAsString(block)}")
            }
        }
    }
}

data class SketchBlockProjection(
        val sketchId: String,
        val name: String,
        val description: String,
        val blockSketch: JsonNode
)

data class PluginInstallationProjection(
        val pluginInstallationId: String,
        val pluginId: String,
        val pluginConfiguration: JsonNode
)