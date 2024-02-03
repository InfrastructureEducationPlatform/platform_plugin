package com.example.demo.web

import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import com.example.demo.web.dto.*
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping

@Service
public class DBApiService {
    suspend fun createDatabaseInstance(
        dbInstanceIdentifierVal: String?,
        block: Block,
        masterUsernameVal: String?,
        masterUserPasswordVal: String?
    ): BlockOutput {
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

        var publicFQDN:String
        val inputRegion:String = (block.features as LinkedHashMap<*, *>)["region"].toString()
        RdsClient { region = inputRegion }.use { rdsClient ->
            val response = rdsClient.createDbInstance(instanceRequest)
            print("The status is ${response.dbInstance?.dbInstanceStatus}")
            publicFQDN = response.dbInstance?.endpoint.toString() + response.dbInstance?.dbInstancePort.toString()
        }
        val rdsOutput = DatabaseOutput(dbInstanceIdentifierVal!!, publicFQDN, masterUsernameVal!!, masterUserPasswordVal!!)

        return BlockOutput(block.id, block.type, inputRegion, rdsOutput)
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