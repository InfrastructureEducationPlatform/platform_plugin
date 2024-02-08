package com.example.demo.web

import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DescribeDbInstancesRequest
import com.example.demo.web.dto.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class DBApiService {
    val log = KotlinLogging.logger {}
    suspend fun createDatabaseInstance(
        dbInstanceIdentifierVal: String?,
        block: Block
    ): BlockOutput {
        if(block.databaseFeatures == null) return BlockOutput(block.id, block.type, "",
            null, null, null,
            "FAIL", "DatabaseFeatures null")

        val inputRegion = block.databaseFeatures!!.region
        val masterUsernameVal = block.databaseFeatures!!.masterUsername
        val masterUserPasswordVal = block.databaseFeatures!!.masterUserPassword
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


        RdsClient { region = inputRegion }.use { rdsClient ->
            val response = rdsClient.createDbInstance(instanceRequest)
            log.info("The status is ${response.dbInstance?.dbInstanceStatus}")

        }
        val publicFQDN = waitForInstanceReady(dbInstanceIdentifierVal, inputRegion)

        val rdsOutput = DatabaseOutput(dbInstanceIdentifierVal!!, publicFQDN, masterUsernameVal!!, masterUserPasswordVal!!)
        return BlockOutput(block.id, block.type, inputRegion!!, null, null, rdsOutput, "OK")
    }

    suspend fun waitForInstanceReady(dbInstanceIdentifierVal: String?, inputRegion: String?): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String
        log.info("Waiting for instance to become available.")

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
                            log.info("...$instanceReadyStr")
                            delay(sleepTime * 1000)
                        }
                    }
                }
            }
            log.info("Database instance is available!")
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
            log.info("The status of the database is ${response.dbInstance?.dbInstanceStatus}")
        }
    }

}