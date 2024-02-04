package com.example.demo.web

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import aws.sdk.kotlin.services.ec2.waiters.waitUntilInstanceRunning
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.VirtualMachineOutput
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Service
public class VMApiService {
    suspend fun createEC2Instance(@RequestParam block: Block, @RequestParam amiId: String): BlockOutput? {
        val request = RunInstancesRequest {
            imageId = amiId
            instanceType = InstanceType.T1Micro
            maxCount = 1
            minCount = 1
        }
        val inputRegion:String = (block.features as LinkedHashMap<*, *>)["region"].toString()
        Ec2Client { region = inputRegion }.use { ec2 ->
            val response = ec2.runInstances(request)
            val instanceId = response.instances?.get(0)?.instanceId
            val ipAddress = response.instances?.get(0)?.publicIpAddress
            val sshPrivateKey = response.instances?.get(0)?.keyName
            val tag = Tag {
                key = "Name"
                value = block.name
            }

            val requestTags = CreateTagsRequest {
                resources = listOf(instanceId.toString())
                tags = listOf(tag)
            }
            ec2.createTags(requestTags)
            ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId.toString())
            }
            println("Successfully started EC2 Instance $instanceId based on AMI $amiId")
            val vmOutput = VirtualMachineOutput(instanceId.toString(), ipAddress.toString(), sshPrivateKey.toString())
            return BlockOutput(block.id, block.type, inputRegion, vmOutput)
        }
    }

    @GetMapping("/startEC2")
    suspend fun startInstanceSc(instanceId: String, inputRegion: String) {
        val request = StartInstancesRequest {
            instanceIds = listOf(instanceId)
        }

        Ec2Client { region = inputRegion }.use { ec2 ->
            ec2.startInstances(request)
            println("Waiting until instance $instanceId starts. This will take a few minutes.")
            ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId)
            }
            println("Successfully started instance $instanceId")
        }
    }

    @GetMapping("/terminateEC2")
    suspend fun terminateEC2(instanceID: String, inputRegion: String) {

        val request = TerminateInstancesRequest {
            instanceIds = listOf(instanceID)
        }

        Ec2Client { region = inputRegion }.use { ec2 ->
            val response = ec2.terminateInstances(request)
            response.terminatingInstances?.forEach { instance ->
                println("The ID of the terminated instance is ${instance.instanceId}")
            }
        }
    }

}