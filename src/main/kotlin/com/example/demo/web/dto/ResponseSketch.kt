package com.example.demo.web.dto

class ResponseSketch(sketchId: String, blockList: List<Block>, blockOutput: List<BlockOutput>) {
    var sketchId:String = ""
    var blockList:List<Block> = ArrayList()
    var blockOutput:List<BlockOutput> = ArrayList()
}