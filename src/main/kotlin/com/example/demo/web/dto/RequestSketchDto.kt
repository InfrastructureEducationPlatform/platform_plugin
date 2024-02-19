package com.example.demo.web.dto

import io.swagger.v3.oas.annotations.media.Schema

data class RequestSketchDto (
    @Schema(description = "스케치 ID")
    var sketchId:String,
    @Schema(description = "스케치 내 블록 리스트")
    var blockList:List<Block>
)