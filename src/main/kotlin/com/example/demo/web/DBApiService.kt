package com.example.demo.web

import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.DatabaseFeatures
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping

@Service
public class DBApiService {
    suspend fun createDatabaseInstance(
        dbInstanceIdentifierVal: String?,
        block: Block,
        masterUsernameVal: String?,
        masterUserPasswordVal: String?
    ) {
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

        RdsClient { region = (block.features as LinkedHashMap<*, *>)["region"].toString() }.use { rdsClient ->
            val response = rdsClient.createDbInstance(instanceRequest)
            print("The status is ${response.dbInstance?.dbInstanceStatus}")
        }
    }

    @GetMapping("/deleteDB")
    suspend fun deleteDatabaseInstance(dbInstanceIdentifierVal: String?) {

        val deleteDbInstanceRequest = DeleteDbInstanceRequest {
            dbInstanceIdentifier = dbInstanceIdentifierVal
            deleteAutomatedBackups = true
            skipFinalSnapshot = true
        }

        RdsClient { region = "us-west-2" }.use { rdsClient ->
            val response = rdsClient.deleteDbInstance(deleteDbInstanceRequest)
            print("The status of the database is ${response.dbInstance?.dbInstanceStatus}")
        }
    }

}