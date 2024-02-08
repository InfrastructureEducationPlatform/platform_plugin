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
        for (block in sketch.blockList) {
            val blockOutput:BlockOutput = when (block.type) {
                "virtualMachine" -> vmApiService.createEC2Instance(block, "ami-0c0b74d29acd0cd97")
                "webServer" -> webApiService.createEBInstance(block)
                "database" -> dbApiService.createDatabaseInstance("test-db", block)
                else -> { BlockOutput(block.id, block.type, "",
                    null, null, null, "ERROR","Block type error") }
            }
            blockOutputList.add(blockOutput)
        }
        return ResponseSketchDto(sketch.sketchId, sketch.blockList, blockOutputList)
    }
}