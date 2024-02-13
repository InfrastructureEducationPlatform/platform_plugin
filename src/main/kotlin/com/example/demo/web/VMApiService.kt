package com.example.demo.web

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import aws.sdk.kotlin.services.ec2.waiters.waitUntilInstanceRunning
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.VirtualMachineOutput
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class VMApiService {
    val log = KotlinLogging.logger {}
    suspend fun isValidVmBlock(block: Block) {
        if(block.virtualMachineFeatures == null) {
            throw CustomException(ErrorCode.INVALID_VM_FEATURES)
        }
        val vmFeatures = block.virtualMachineFeatures!!

        if(vmFeatures.region == "" || vmFeatures.osType == "" || vmFeatures.tier == "") {
            throw CustomException(ErrorCode.INVALID_VM_FEATURES)
        }
    }
    suspend fun createEC2Instance(block: Block, amiId: String): BlockOutput {
        val request = RunInstancesRequest {
            imageId = amiId
            instanceType = InstanceType.T1Micro
            maxCount = 1
            minCount = 1
        }
        val inputRegion:String = block.virtualMachineFeatures!!.region

        Ec2Client { region = inputRegion }.use { ec2 ->
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
            val waitResponse = ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId.toString())
            }

            val ipAddress = waitResponse.getOrThrow().reservations?.get(0)?.instances?.get(0)?.publicIpAddress
            val sshPrivateKey = waitResponse.getOrThrow().reservations?.get(0)?.instances?.get(0)?.keyName

            log.info("Successfully started EC2 Instance $instanceId based on AMI $amiId")
            val vmOutput = VirtualMachineOutput(instanceId.toString(), ipAddress.toString(), sshPrivateKey.toString())
            return BlockOutput(block.id, block.type, inputRegion, vmOutput, null, null)
        }
    }

    suspend fun startInstanceSc(instanceId: String, inputRegion: String) {
        val request = StartInstancesRequest {
            instanceIds = listOf(instanceId)
        }

        Ec2Client { region = inputRegion }.use { ec2 ->
            ec2.startInstances(request)
            log.info("Waiting until instance $instanceId starts. This will take a few minutes.")
            ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId)
            }
            log.info("Successfully started instance $instanceId")
        }
    }

    suspend fun terminateEC2(instanceID: String, inputRegion: String) {

        val request = TerminateInstancesRequest {
            instanceIds = listOf(instanceID)
        }

        Ec2Client { region = inputRegion }.use { ec2 ->
            val response = ec2.terminateInstances(request)
            response.terminatingInstances?.forEach { instance ->
                log.info("The ID of the terminated instance is ${instance.instanceId}")
            }
        }
    }

}