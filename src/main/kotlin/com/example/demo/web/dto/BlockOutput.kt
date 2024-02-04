package com.example.demo.web.dto

data class BlockOutput(
    var id: String,
    var type: String,
    var region: String,
    var virtualMachineOutput: VirtualMachineOutput?,
    var webServerOutput: WebServerOutput?,
    var databaseOutput: DatabaseOutput?,
)

data class VirtualMachineOutput (
    var instanceId:String,
    var ipAddress:String,
    var sshPrivateKey:String
)


data class WebServerOutput (
    var appName:String,
    var publicFQDN:String
)

data class DatabaseOutput (
    var dbInstanceIdentifierVal:String,
    var publicFQDN:String,
    var databaseUsername:String,
    var databasePassword:String
)