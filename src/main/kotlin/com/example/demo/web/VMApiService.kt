package com.example.demo.web

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import aws.sdk.kotlin.services.ec2.waiters.waitUntilInstanceRunning
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.VirtualMachineFeatures
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

@Service
public class VMApiService {
    suspend fun createEC2Instance(@RequestParam block: Block, @RequestParam amiId: String): String? {
        val request = RunInstancesRequest {
            imageId = amiId
            instanceType = InstanceType.T1Micro
            maxCount = 1
            minCount = 1
        }

        Ec2Client { region = (block.features as LinkedHashMap<*, *>)["region"].toString() }.use { ec2 ->
            val response = ec2.runInstances(request)
            val instanceId = response.instances?.get(0)?.instanceId
            val tag = Tag {
                key = "Name"
                value = block.name
            }

            val requestTags = CreateTagsRequest {
                resources = listOf(instanceId.toString())
                tags = listOf(tag)
            }
            ec2.createTags(requestTags)
            println("Successfully started EC2 Instance $instanceId based on AMI $amiId")
            return instanceId
        }
    }

    @GetMapping("/startEC2")
    suspend fun startInstanceSc(instanceId: String) {
        val request = StartInstancesRequest {
            instanceIds = listOf(instanceId)
        }

        Ec2Client { region = "us-west-2" }.use { ec2 ->
            ec2.startInstances(request)
            println("Waiting until instance $instanceId starts. This will take a few minutes.")
            ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId)
            }
            println("Successfully started instance $instanceId")
        }
    }

    @GetMapping("/terminateEC2")
    suspend fun terminateEC2(instanceID: String) {

        val request = TerminateInstancesRequest {
            instanceIds = listOf(instanceID)
        }

        Ec2Client { region = "us-west-2" }.use { ec2 ->
            val response = ec2.terminateInstances(request)
            response.terminatingInstances?.forEach { instance ->
                println("The ID of the terminated instance is ${instance.instanceId}")
            }
        }
    }

}