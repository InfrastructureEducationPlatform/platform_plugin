package com.example.demo.web

import com.example.demo.web.dto.AwsConfiguration
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.RequestSketchDto
import com.example.demo.web.dto.ResponseSketchDto
import com.example.demo.web.service.aws.DBApiService
import com.example.demo.web.service.aws.VMApiService
import com.example.demo.web.service.aws.VpcService
import com.example.demo.web.service.aws.WebApiService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.web.bind.annotation.*

@Tag(name = "Deployment", description = "배포 API")
@RestController
class ApiController(
    private val vmApiService: VMApiService,
    private val webApiService: WebApiService,
    private val dbApiService: DBApiService,
    private val vpcService: VpcService
) {
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "배포 요청 성공", content = [
            Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ResponseSketchDto::class)))
        ]),
        ApiResponse(responseCode = "400", description = "잘못된 파라미터 값 입력", content = [Content()])
    ])
    @Operation(summary = "Sketch 배포 테스트 API")
    @PostMapping("/sketch/deployment")
    suspend fun deploymentSketch(@RequestBody sketch: RequestSketchDto): ResponseSketchDto {
        // Create AWS Credential
        // {"Region": "Default Region Code(i.e: ap-northeast-2)", "AccessKey": "Access Key ID", "SecretKey": "Access Secret Key"}
        val awsCredential = AwsConfiguration(
                region = sketch.pluginInstallationInformation["Region"].asText(),
                accessKeyId = sketch.pluginInstallationInformation["AccessKey"].asText(),
                secretAccessKey = sketch.pluginInstallationInformation["SecretKey"].asText()
        )

        //Service 가능 여부 체크
        for (block in sketch.blockList) {
            when (block.type) {
                "virtualMachine" -> vmApiService.isValidVmBlock(block)
                "webServer" -> webApiService.isValidWebBlock(block)
                "database" -> dbApiService.isValidDbBlock(block)
                else -> {
                    throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                }
            }
        }

        val vpc = vpcService.createVpc(awsCredential)
        //Service 배포
        val blockOutputDeferredList = mutableListOf<Deferred<BlockOutput>>()
        coroutineScope {
            for (block in sketch.blockList) {
                val blockOutputDeferred = async {
                    when (block.type) {
                        "virtualMachine" -> vmApiService.createEC2Instance(awsCredential, block, "ami-0f3a440bbcff3d043", vpc)
                        "webServer" -> webApiService.createEBInstance(block, awsCredential, vpc)
                        "database" -> dbApiService.createDatabaseInstance(block.name, block, awsCredential, vpc)
                        else -> {
                            throw CustomException(ErrorCode.INVALID_BLOCK_TYPE)
                        }
                    }
                }
                blockOutputDeferredList.add(blockOutputDeferred)
            }
        }
        val blockOutputList = blockOutputDeferredList.map { it.await() }

        return ResponseSketchDto(sketch.sketchId, sketch.blockList, blockOutputList)
    }

    @DeleteMapping("/sketch/vm")
    @Operation(summary = "VM 삭제", description = "VM 인스턴스를 삭제합니다.")
    suspend fun vmDelete(@Parameter(description = "VM 인스턴스 ID", required = true) @RequestParam("instanceID") instanceId: String,
                         @Parameter(description = "VM 생성 지역", required = true) @RequestParam("region", defaultValue = "us-east-1") region: String,
                         @Parameter(description = "AWS config", required = true) awsConfiguration: AwsConfiguration) {
        vmApiService.terminateEC2(instanceId, awsConfiguration)
    }

    @DeleteMapping("/sketch/web")
    @Operation(summary = "웹 서버 삭제", description = "웹 서버를 삭제합니다.")
    suspend fun webDelete(@Parameter(description = "웹 서버 이름", required = true) @RequestParam("name") appName: String,
                          @Parameter(description = "웹 생성 지역", required = true) @RequestParam("region", defaultValue = "us-east-1") region: String,
                          @Parameter(description = "AWS config", required = true) awsConfiguration: AwsConfiguration) {
        webApiService.deleteApp(appName, awsConfiguration)
    }

    @DeleteMapping("/sketch/db")
    @Operation(summary = "DB 삭제", description = "DB 인스턴스를 삭제합니다.")
    suspend fun dbDelete(@Parameter(description = "삭제 요청 DB 식별자", required = true) @RequestParam("dbInstanceIdentifierVal") dbInstanceIdentifierVal: String,
                         @Parameter(description = "DB 생성 지역", required = true) @RequestParam("region", defaultValue = "us-east-1") region: String,
                         @Parameter(description = "AWS config", required = true) awsConfiguration: AwsConfiguration) {
        dbApiService.deleteDatabaseInstance(dbInstanceIdentifierVal, awsConfiguration)
    }

}