package com.example.demo.web.dto

data class Block(
    var x: Int = 0,
    var y: Int = 0,
    var id: String = "",
    var name: String = "",
    var tags: List<String> = ArrayList(),
    var type: String = "",
    var description: String = "",
    var advancedMeta: LinkedHashMap<String, String>? = null,
    var virtualMachineFeatures: VirtualMachineFeatures? = null,
    var webServerFeatures: WebServerFeatures? = null,
    var databaseFeatures: DatabaseFeatures? = null
)
data class VirtualMachineFeatures (
    var tier:String,
    var osType:String,
    var region:String
)
data class WebServerFeatures (
    var tier:String,
    var region:String,
    var containerMetadata: ContainerMetadata
)
data class ContainerMetadata (
    var imageTags:String,
    var registryUrl:String
)
data class DatabaseFeatures (
    var tier:String,
    var region:String,
    var masterUsername:String,
    var masterUserPassword:String
)