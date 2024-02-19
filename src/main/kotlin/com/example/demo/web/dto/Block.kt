package com.example.demo.web.dto

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
)
data class VirtualMachineFeatures (
    @Schema(description = "VM 요금 정보")
    var tier:String,
    @Schema(description = "VM 운영 체제 유형")
    var osType:String,
    @Schema(description = "VM 생성 지역")
    var region:String
)
data class WebServerFeatures (
    @Schema(description = "Web Server 요금 정보")
    var tier:String,
    @Schema(description = "Web Server 생성 지역")
    var region:String,
    @Schema(description = "Web Server 메타 데이터")
    var containerMetadata: ContainerMetadata
)
data class ContainerMetadata (
    @Schema(description = "웹 서버 이미지 태그")
    var imageTags:String,
    @Schema(description = "웹 서버 url")
    var registryUrl:String
)
data class DatabaseFeatures (
    @Schema(description = "DB 요금 정보")
    var tier:String,
    @Schema(description = "DB 생성 지역")
    var region:String,
    @Schema(description = "DB 사용자 이름")
    var masterUsername:String,
    @Schema(description = "DB 사용자 비밀번호")
    var masterUserPassword:String
)