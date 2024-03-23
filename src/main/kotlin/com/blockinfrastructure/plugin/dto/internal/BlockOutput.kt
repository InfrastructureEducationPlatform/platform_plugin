package com.blockinfrastructure.plugin.dto.internal

import io.swagger.v3.oas.annotations.media.Schema

data class BlockOutput(
        @Schema(description = "블록의 ID")
        val id: String,
        @Schema(description = "블록의 타입")
        val type: String,
        @Schema(description = "VM 블록 배포 Output")
        val virtualMachineOutput: VirtualMachineOutput?,
        @Schema(description = "웹 서버 블록 배포 Output")
        val webServerOutput: WebServerOutput?,
        @Schema(description = "DB 블록 배포 Output")
        val databaseOutput: DatabaseOutput?
)

data class VirtualMachineOutput(
        @Schema(description = "VM 인스턴스 ID")
        val instanceId: String,
        @Schema(description = "VM 인스턴스 ip")
        val ipAddress: String,
        @Schema(description = "VM 인스턴스 ssh 키")
        val sshPrivateKey: String
)


data class WebServerOutput(
        @Schema(description = "웹 서버 어플리케이션 이름")
        val appName: String,
        @Schema(description = "웹 서버 주소")
        val publicFQDN: String
)

data class DatabaseOutput(
        @Schema(description = "DB 인스턴스 식별자")
        val dbInstanceIdentifierVal: String,
        @Schema(description = "DB 주소")
        val publicFQDN: String,
        @Schema(description = "DB 사용자 이름")
        val databaseUsername: String,
        @Schema(description = "DB 사용자 비밀번호")
        val databasePassword: String
)