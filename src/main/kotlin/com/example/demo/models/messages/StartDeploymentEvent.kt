package com.example.demo.models.messages

import com.fasterxml.jackson.databind.JsonNode

data class StartDeploymentEvent(
        val deploymentLogId: String,
        val sketchProjection: SketchBlockProjection,
        val pluginInstallationProjection: PluginInstallationProjection
)

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