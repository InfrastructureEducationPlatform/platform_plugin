package com.blockinfrastructure.plugin.dto.request

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class DeploymentReportRequestDto(
        val deploymentId: String,
        val reportType: DeploymentReportType,

        // One of: DeploymentOutputRequestDto
        val data: JsonNode? = null
) {
    fun toDeploymentOutputRequestDto(): DeploymentOutputRequestDto {
        if (reportType != DeploymentReportType.DEPLOYED) {
            throw IllegalStateException("reportType is not DEPLOYED")
        }

        return jacksonObjectMapper().convertValue(data, DeploymentOutputRequestDto::class.java)
    }
}

enum class DeploymentReportType {
    DEPLOYED,
    DESTROYED,
    FAILED
}