package com.example.demo.web.dto

data class ResponseSketchDto (
    var sketchId:String,
    var blockList:List<Block>,
    var blockOutput:List<BlockOutput>
)