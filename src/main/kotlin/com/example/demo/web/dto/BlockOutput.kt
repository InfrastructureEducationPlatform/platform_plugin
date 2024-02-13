package com.example.demo.web.dto

data class BlockOutput(
    val id: String,
    val type: String,
    val region: String,
    val virtualMachineOutput: VirtualMachineOutput?,
    val webServerOutput: WebServerOutput?,
    val databaseOutput: DatabaseOutput?
)

data class VirtualMachineOutput (
    val instanceId:String,
    val ipAddress:String,
    val sshPrivateKey:String
)


data class WebServerOutput (
    val appName:String,
    val publicFQDN:String
)

data class DatabaseOutput (
    val dbInstanceIdentifierVal:String,
    val publicFQDN:String,
    val databaseUsername:String,
    val databasePassword:String
)