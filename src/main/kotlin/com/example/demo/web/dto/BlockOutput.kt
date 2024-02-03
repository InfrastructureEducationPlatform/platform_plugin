package com.example.demo.web.dto

class BlockOutput(
    var id: String,
    var type: String,
    var region: String,
    var output: Any
)

data class VirtualMachineOutput (
    var instanceID:String = "",
    var ipAddress:String = "",
    var sshPrivateKey:String = ""
)


data class WebServerOutput (
    var appName:String = "",
    var publicFQDN:String = ""
)

data class DatabaseOutput (
    var dbInstanceIdentifierVal:String = "",
    var publicFQDN:String = "",
    var databaseUsername:String = "",
    var databasePassword:String
)