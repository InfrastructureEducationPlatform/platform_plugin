package com.blockinfrastructure.plugin.controllers

import com.blockinfrastructure.plugin.dto.request.DeploymentOutputRequestDto
import com.blockinfrastructure.plugin.legacy.dto.ResponseSketchDto
import com.blockinfrastructure.plugin.service.DeploymentReceiveService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/deployment/receive")
class DeploymentReceiveController(
        private val deploymentReceiveService: DeploymentReceiveService
) {
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "배포 요청 성공", content = [
            Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ResponseSketchDto::class)))
        ]),
        ApiResponse(responseCode = "400", description = "잘못된 파라미터 값 입력", content = [Content()])
    ])
    @Operation(summary = "Github Actions 배포 response receive API")
    @PostMapping("output")
    suspend fun receiveOutputs(@RequestBody receiveRequest: DeploymentOutputRequestDto) {
        deploymentReceiveService.sendDeploymentEventOutput(receiveRequest)
    }
}