package com.example.demo.web.dto

data class Block(
    var x: Int,
    var y: Int,
    var id: String,
    var name: String,
    var tags: List<String>,
    var type: String,
    var description: String,
    var advancedMeta: Any,
    var virtualMachineFeatures: VirtualMachineFeatures?,
    var webServerFeatures: WebServerFeatures?,
    var databaseFeatures: DatabaseFeatures?
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
    var imageTags:String?,
    var registryUrl:String?
)
data class DatabaseFeatures (
    var tier:String,
    var region:String,
    var masterUsername:String,
    var masterUserPassword:String
)