package com.example.demo.web.dto

class Block {
    var x:Int = 0
    var y:Int = 0
    var id:String = ""
    var name:String = ""
    var tags = ArrayList<String>()
    var type:String = ""
    var description:String = ""
    var advancedMeta:Any = ""
    var features:Any = ""
}
data class VirtualMachineFeatures (
    var tier:String = "",
    var osType:String = "",
    var region:String = ""
)
data class WebServerFeatures (
    var tier:String = "",
    var region:String = "",
    var containerMetadata: ContainerMetadata
)
data class ContainerMetadata (
    var imageTags:String = "",
    var registerUrl:String = ""
)
data class DatabaseFeatures (
    var tier:String = "",
    var region:String = ""
)