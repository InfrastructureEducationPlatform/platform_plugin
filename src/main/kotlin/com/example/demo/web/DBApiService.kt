package com.example.demo.web

import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DescribeDbInstancesRequest
import com.example.demo.web.dto.*
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping

@Service
public class DBApiService {
    suspend fun createDatabaseInstance(
        dbInstanceIdentifierVal: String?,
        block: Block
    ): BlockOutput {
        val inputRegion:String = (block.features as LinkedHashMap<*, *>)["region"].toString()
        val masterUsernameVal:String = (block.features as LinkedHashMap<*, *>)["masterUsername"].toString()
        val masterUserPasswordVal:String = (block.features as LinkedHashMap<*, *>)["masterUserPassword"].toString()
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

        val publicFQDN:String
        RdsClient { region = inputRegion }.use { rdsClient ->
            val response = rdsClient.createDbInstance(instanceRequest)
            print("The status is ${response.dbInstance?.dbInstanceStatus}")
            publicFQDN = response.dbInstance?.endpoint.toString() + response.dbInstance?.dbInstancePort.toString()
        }
        waitForInstanceReady(dbInstanceIdentifierVal, inputRegion)

        val rdsOutput = DatabaseOutput(dbInstanceIdentifierVal!!, publicFQDN, masterUsernameVal, masterUserPasswordVal)
        return BlockOutput(block.id, block.type, inputRegion, rdsOutput)
    }

    suspend fun waitForInstanceReady(dbInstanceIdentifierVal: String?, inputRegion: String?) {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr = ""
        println("Waiting for instance to become available.")

        val instanceRequest = DescribeDbInstancesRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
        }

        RdsClient { region = inputRegion }.use { rdsClient ->
            while (!instanceReady) {
                val response = rdsClient.describeDbInstances(instanceRequest)
                val instanceList = response.dbInstances
                if (instanceList != null) {
                    for (instance in instanceList) {
                        instanceReadyStr = instance.dbInstanceStatus.toString()
                        if (instanceReadyStr.contains("available")) {
                            instanceReady = true
                        } else {
                            println("...$instanceReadyStr")
                            delay(sleepTime * 1000)
                        }
                    }
                }
            }
            println("Database instance is available!")
        }
    }

    @GetMapping("/deleteDB")
    suspend fun deleteDatabaseInstance(dbInstanceIdentifierVal: String?, inputRegion: String?) {

        val deleteDbInstanceRequest = DeleteDbInstanceRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
            deleteAutomatedBackups = true
            skipFinalSnapshot = true
        }

        RdsClient { region = inputRegion }.use { rdsClient ->
            val response = rdsClient.deleteDbInstance(deleteDbInstanceRequest)
            print("The status of the database is ${response.dbInstance?.dbInstanceStatus}")
        }
    }

}