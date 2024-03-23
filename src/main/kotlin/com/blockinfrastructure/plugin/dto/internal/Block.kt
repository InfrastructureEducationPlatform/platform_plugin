package com.blockinfrastructure.plugin.dto.internal

import io.swagger.v3.oas.annotations.media.Schema

data class Block(
        @Schema(description = "블록의 x좌표")
        var x: Int,
        @Schema(description = "블록의 y좌표")
        var y: Int,
        @Schema(description = "블록의 ID")
        var id: String,
        @Schema(description = "블록의 이름")
        var name: String,
        @Schema(description = "블록의 태그")
        var tags: List<String>,
        @Schema(description = "블록의 타입", allowableValues = ["virtualMachine", "webServer", "database"])
        var type: String,
        @Schema(description = "블록의 설명")
        var description: String,
        @Schema(description = "블록의 메타 데이터")
        var advancedMeta: LinkedHashMap<String, String>?,
        @Schema(description = "VM 블록 Features")
        var virtualMachineFeatures: VirtualMachineFeatures?,
        @Schema(description = "웹 서버 블록 Features")
        var webServerFeatures: WebServerFeatures?,
        @Schema(description = "DB 블록 Features")
        var databaseFeatures: DatabaseFeatures?
) {
    fun isValidBlock(): Boolean {
        return when (type) {
            "virtualMachine" -> virtualMachineFeatures?.isValidVmFeatures() == true
            "webServer" -> webServerFeatures?.isValidWebFeatures() == true && name.matches(Regex("^.{1,100}$"))
            "database" -> databaseFeatures?.isValidDbFeatures() == true && name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$"))
            else -> false
        }
    }
}

data class VirtualMachineFeatures(
        @Schema(description = "VM 요금 정보")
        var tier: String,
        @Schema(description = "VM 운영 체제 유형")
        var osType: String
) {
    fun isValidVmFeatures(): Boolean {
        return osType.isNotEmpty() && tier.isNotEmpty()
    }
}

data class WebServerFeatures(
        @Schema(description = "Web Server 요금 정보")
        var tier: String,
        @Schema(description = "Web Server 메타 데이터")
        var containerMetadata: ContainerMetadata
) {
    fun isValidWebFeatures(): Boolean {
        return tier.isNotEmpty() && containerMetadata.imageTags.isNotEmpty() && containerMetadata.registryUrl.isNotEmpty()
    }
}

data class ContainerMetadata(
        @Schema(description = "웹 서버 이미지 태그")
        var imageTags: String,
        @Schema(description = "웹 서버 url")
        var registryUrl: String
)

data class DatabaseFeatures(
        @Schema(description = "DB 요금 정보")
        var tier: String,
        @Schema(description = "DB 사용자 이름")
        var masterUsername: String = "admin",
        @Schema(description = "DB 사용자 비밀번호")
        var masterUserPassword: String = "testPassword!"
) {
    fun isValidDbFeatures(): Boolean {
        val basicMetadataSatisfied = tier.isNotEmpty() && masterUsername.isNotEmpty() && masterUserPassword.isNotEmpty()

        return basicMetadataSatisfied &&
                masterUsername.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{0,15}$")) &&
                masterUserPassword.matches(Regex("[^\"/@]{8,128}"))
    }
}