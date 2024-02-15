package com.example.demo.web

import aws.sdk.kotlin.services.ec2.model.Ec2Exception
import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DescribeDbInstancesRequest
import aws.sdk.kotlin.services.rds.model.RdsException
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import com.example.demo.web.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service

@Service
class DBApiService {
    val log = KotlinLogging.logger {}
    suspend fun isValidDbBlock(block: Block) {
        if(block.databaseFeatures == null) {
            throw CustomException(ErrorCode.INVALID_DB_FEATURES)
        }
        val dbFeatures = block.databaseFeatures!!
        if(dbFeatures.region == "" || dbFeatures.tier == "" || dbFeatures.masterUsername == "" || dbFeatures.masterUserPassword == "") {
            throw CustomException(ErrorCode.INVALID_DB_FEATURES)
        }

        val regexObj = RegexObj()
        if(!regexObj.verifyDbName(block.name)) {
            throw CustomException(ErrorCode.INVALID_DB_NAME)
        }
        if(!regexObj.verifyDbUserName(dbFeatures.masterUsername)) {
            throw CustomException(ErrorCode.INVALID_DB_USERNAME)
        }
        if(!regexObj.verifyDbUserPassword(dbFeatures.masterUserPassword)) {
            throw CustomException(ErrorCode.INVALID_DB_USER_PASSWORD)
        }
    }
    suspend fun createDatabaseInstance(
        dbInstanceIdentifierVal: String?,
        block: Block
    ): BlockOutput {

        val dbFeatures = block.databaseFeatures!!
        val inputRegion = dbFeatures.region
        val masterUsernameVal = dbFeatures.masterUsername
        val masterUserPasswordVal = dbFeatures.masterUserPassword

        val instanceRequest = CreateDbInstanceRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
            allocatedStorage = 20
            dbName = block.name
            engine = "postgres"
            dbInstanceClass = "db.t3.micro"
            engineVersion = "15.4"
            storageType = "standard"
            masterUsername = masterUsernameVal
            masterUserPassword = masterUserPasswordVal
        }

        try {
            RdsClient { region = inputRegion }.use { rdsClient ->
                val response = rdsClient.createDbInstance(instanceRequest)
                log.info { "The status is ${response.dbInstance?.dbInstanceStatus}" }

            }
            val publicFQDN = waitForInstanceReady(dbInstanceIdentifierVal, inputRegion)
            val rdsOutput = DatabaseOutput(dbInstanceIdentifierVal!!, publicFQDN, masterUsernameVal,
                masterUserPasswordVal
            )
            return BlockOutput(block.id, block.type, inputRegion, null, null, rdsOutput)
        } catch (ex: RdsException) {
            val awsRequestId = ex.sdkErrorMetadata.requestId
            val httpResp = ex.sdkErrorMetadata.protocolResponse as? HttpResponse
            val errorMessage = ex.sdkErrorMetadata.errorMessage

            log.error { "requestId was: $awsRequestId" }
            log.error { "http status code was: ${httpResp?.status}" }
            log.error { "error message was: $errorMessage" }

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }
    }

    suspend fun waitForInstanceReady(dbInstanceIdentifierVal: String?, inputRegion: String?): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String
        log.info { "Waiting for instance to become available." }

        val instanceRequest = DescribeDbInstancesRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
        }

        var publicFQDN = ""
        RdsClient { region = inputRegion }.use { rdsClient ->
            while (!instanceReady) {
                val response = rdsClient.describeDbInstances(instanceRequest)
                val instanceList = response.dbInstances
                if (instanceList != null) {
                    for (instance in instanceList) {
                        instanceReadyStr = instance.dbInstanceStatus.toString()
                        if (instanceReadyStr.contains("available")) {
                            instanceReady = true
                            publicFQDN = instance.endpoint?.address + instance.endpoint?.port
                        } else {
                            log.info { "...$instanceReadyStr" }
                            delay(sleepTime * 1000)
                        }
                    }
                }
            }
            log.info { "Database instance is available!" }
        }
        return publicFQDN
    }

    suspend fun deleteDatabaseInstance(dbInstanceIdentifierVal: String?, inputRegion: String?) {

        val deleteDbInstanceRequest = DeleteDbInstanceRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
            deleteAutomatedBackups = true
            skipFinalSnapshot = true
        }

        RdsClient { region = inputRegion }.use { rdsClient ->
            val response = rdsClient.deleteDbInstance(deleteDbInstanceRequest)
            log.info { "The status of the database is ${response.dbInstance?.dbInstanceStatus}" }
        }
    }

}