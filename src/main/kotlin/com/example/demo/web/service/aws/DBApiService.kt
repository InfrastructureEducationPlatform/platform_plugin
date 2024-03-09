package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.*
import com.example.demo.utils.CommonUtils
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.RegexObj
import com.example.demo.web.dto.*
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service

@Service
class DBApiService {
    suspend fun isValidDbBlock(block: Block) {
        if (block.databaseFeatures == null) {
            throw CustomException(ErrorCode.INVALID_DB_FEATURES)
        }
        val dbFeatures = block.databaseFeatures!!
        if (dbFeatures.tier == "" || dbFeatures.masterUsername == "" || dbFeatures.masterUserPassword == "") {
            throw CustomException(ErrorCode.INVALID_DB_FEATURES)
        }

        val regexObj = RegexObj()
        if (!regexObj.verifyDbName(block.name)) {
            throw CustomException(ErrorCode.INVALID_DB_NAME)
        }
        if (!regexObj.verifyDbUserName(dbFeatures.masterUsername)) {
            throw CustomException(ErrorCode.INVALID_DB_USERNAME)
        }
        if (!regexObj.verifyDbUserPassword(dbFeatures.masterUserPassword)) {
            throw CustomException(ErrorCode.INVALID_DB_USER_PASSWORD)
        }
    }

    suspend fun createDatabaseInstance(
            dbInstanceIdentifierVal: String?,
            block: Block,
            awsConfiguration: AwsConfiguration,
            vpc: CreateVpcDto
    ): BlockOutput {
        val dbFeatures = block.databaseFeatures!!
        val masterUsernameVal = dbFeatures.masterUsername
        val masterUserPasswordVal = dbFeatures.masterUserPassword
        val dbSubnetGroupNameVal = "iep-db-subnet-group"

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
            dbSubnetGroupName = dbSubnetGroupNameVal
            vpcSecurityGroupIds = listOf(vpc.securityGroupIds[1])
            publiclyAccessible = true
        }

        try {
            RdsClient {
                region = awsConfiguration.region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                }
            }.use { rdsClient ->
                try {
                    val instanceSubnetRequest = CreateDbSubnetGroupRequest {
                        subnetIds = vpc.subnetIds
                        dbSubnetGroupName = dbSubnetGroupNameVal
                        dbSubnetGroupDescription = "iep-db subnet group"
                    }
                    rdsClient.createDbSubnetGroup(instanceSubnetRequest)
                }catch (e:DbSubnetGroupAlreadyExistsFault) {
                    log.info {"$dbSubnetGroupNameVal is already exist"}
                }


                delay(1000)
                val response = rdsClient.createDbInstance(instanceRequest)

                log.info { "The status is ${response.dbInstance?.dbInstanceStatus}" }
            }
            val publicFQDN = waitForInstanceReady(dbInstanceIdentifierVal, awsConfiguration)
            val rdsOutput = DatabaseOutput(dbInstanceIdentifierVal!!, publicFQDN, masterUsernameVal,
                    masterUserPasswordVal
            )
            return BlockOutput(block.id, block.type, null, null, rdsOutput)
        } catch (ex: RdsException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }
    }

    suspend fun waitForInstanceReady(dbInstanceIdentifierVal: String?, awsConfiguration: AwsConfiguration): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String
        log.info { "Waiting for instance to become available." }

        val instanceRequest = DescribeDbInstancesRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
        }

        var publicFQDN = ""
        RdsClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { rdsClient ->
            while (!instanceReady) {
                val response = rdsClient.describeDbInstances(instanceRequest)
                val instanceList = response.dbInstances
                if (instanceList != null) {
                    for (instance in instanceList) {
                        instanceReadyStr = instance.dbInstanceStatus.toString()
                        if (instanceReadyStr.contains("available")) {
                            instanceReady = true
                            publicFQDN = instance.endpoint?.address + ":" + instance.endpoint?.port
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

    suspend fun deleteDb(block: Block, awsConfiguration: AwsConfiguration):Boolean {
        val dbSubnetGroupNameVal = "iep-db-subnet-group"
        try {
            deleteDatabaseInstance(block.name, awsConfiguration)
        } catch (_:DbInstanceNotFoundFault) {
            log.info { "${block.name} db is already deleted" }
        }

        try {
            RdsClient {
                region = awsConfiguration.region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                }
            }.use { rdsClient ->
                val instanceSubnetRequest = DeleteDbSubnetGroupRequest {
                    dbSubnetGroupName = dbSubnetGroupNameVal
                }
                rdsClient.deleteDbSubnetGroup(instanceSubnetRequest)
            }
        } catch (_:DbSubnetGroupNotFoundFault) {
            log.info { "$dbSubnetGroupNameVal is already deleted" }
        }

        return true
    }

    private suspend fun waitForInstanceDeleted(dbInstanceIdentifierVal: String?, awsConfiguration: AwsConfiguration) {
        val sleepTime: Long = 20
        var instanceDeleted = false
        log.info { "Waiting for instance deletion to complete." }

        val instanceRequest = DescribeDbInstancesRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
        }

        val rdsClient = RdsClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }

        try {
            while(true) {
                val response = rdsClient.describeDbInstances(instanceRequest)
                val instanceList = response.dbInstances
                if (instanceList.isNullOrEmpty()) break
                else {
                    log.info { "...DB Deleting" }
                    delay(sleepTime * 1000)
                }
            }
        } catch (_:DbInstanceNotFoundFault) {

        } finally {
            log.info { "Database instance deletion complete!" }
        }
    }

    suspend fun deleteDatabaseInstance(dbInstanceIdentifierVal: String?, awsConfiguration: AwsConfiguration) {

        val deleteDbInstanceRequest = DeleteDbInstanceRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
            deleteAutomatedBackups = true
            skipFinalSnapshot = true
        }

        RdsClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { rdsClient ->
            val response = rdsClient.deleteDbInstance(deleteDbInstanceRequest)
            log.info { "The status of the database is ${response.dbInstance?.dbInstanceStatus}" }
        }

        waitForInstanceDeleted(dbInstanceIdentifierVal, awsConfiguration)
    }

}