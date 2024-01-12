package com.example.demo.web

import com.example.demo.web.dto.RequestSketch
import com.example.demo.web.dto.ResponseSketch
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
    suspend fun deploymentSketch(@RequestBody sketch: RequestSketch): ResponseSketch {
        for (block in sketch.blockList) {
            when (block.type) {
                "virtualMachine" -> vmApiService.createEC2Instance(block, "ami-0c0b74d29acd0cd97")
                "webServer" -> webApiService.createEBInstance(block)
                "database" -> dbApiService.createDatabaseInstance("test-db", block, "choish20", "testtest")
            }
        }

        return ResponseSketch()
    }
}