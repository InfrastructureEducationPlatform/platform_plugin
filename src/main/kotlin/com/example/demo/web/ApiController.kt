package com.example.demo.web

import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.RequestSketchDto
import com.example.demo.web.dto.ResponseSketchDto
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController(
    private val vmApiService: VMApiService,
    private val webApiService: WebApiService,
    private val dbApiService: DBApiService
) {
    @PostMapping("/deploymentSketch")
    suspend fun deploymentSketch(@RequestBody sketch: RequestSketchDto): ResponseSketchDto {
        val blockOutputList = ArrayList<BlockOutput>()

        //Service 가능 여부 체크
        for (block in sketch.blockList) {
            when (block.type) {
                "virtualMachine" -> vmApiService.isValidVmBlock(block)
                "webServer" -> webApiService.isValidWebBlock(block)
                "database" -> dbApiService.isValidDbBlock(block)
                else -> { throw CustomException(ErrorCode.INVALID_BLOCK_TYPE) }
            }
        }

        //Service 배포
        for (block in sketch.blockList) {
            val blockOutput:BlockOutput = when (block.type) {
                "virtualMachine" -> vmApiService.createEC2Instance(block, "ami-0c0b74d29acd0cd97")
                "webServer" -> webApiService.createEBInstance(block)
                "database" -> dbApiService.createDatabaseInstance("test-db", block)
                else -> { throw CustomException(ErrorCode.INVALID_BLOCK_TYPE) }
            }
            blockOutputList.add(blockOutput)
        }
        return ResponseSketchDto(sketch.sketchId, sketch.blockList, blockOutputList)
    }
}