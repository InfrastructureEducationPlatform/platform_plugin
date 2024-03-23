package com.blockinfrastructure.plugin.legacy.dto

import com.blockinfrastructure.plugin.dto.internal.Block
import com.blockinfrastructure.plugin.dto.internal.BlockOutput
import io.swagger.v3.oas.annotations.media.Schema

data class ResponseSketchDto(
        @Schema(description = "스케치 ID")
        var sketchId: String,
        @Schema(description = "스케치 내 블록 리스트")
        var blockList: List<Block>,
        @Schema(description = "블록 Output 리스트")
        var blockOutput: List<BlockOutput>
)