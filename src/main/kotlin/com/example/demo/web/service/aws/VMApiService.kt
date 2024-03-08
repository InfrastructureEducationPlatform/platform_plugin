package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import aws.sdk.kotlin.services.ec2.waiters.waitUntilInstanceRunning
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.example.demo.utils.CommonUtils
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.dto.*
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class VMApiService {
    suspend fun isValidVmBlock(block: Block) {
        if (block.virtualMachineFeatures == null) {
            throw CustomException(ErrorCode.INVALID_VM_FEATURES)
        }
        val vmFeatures = block.virtualMachineFeatures!!

        if (vmFeatures.region == "" || vmFeatures.osType == "" || vmFeatures.tier == "") {
            throw CustomException(ErrorCode.INVALID_VM_FEATURES)
        }
    }

    suspend fun createEC2Instance(awsConfiguration: AwsConfiguration, block: Block, amiId: String, vpc: CreateVpcDto): BlockOutput {
        val inputRegion: String = block.virtualMachineFeatures!!.region

        try {
            Ec2Client {
                region = awsConfiguration.region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                    region = awsConfiguration.region
                }
            }.use { ec2 ->
                val keyPairRequest = CreateKeyPairRequest {
                    keyName = "aws-keypair-${Random.nextInt(1000)}"
                }
                val keyPairResponse = ec2.createKeyPair(keyPairRequest)

                val request = RunInstancesRequest {
                    imageId = amiId
                    instanceType = InstanceType.T2Micro
                    maxCount = 1
                    minCount = 1
                    keyName = keyPairResponse.keyName
                    networkInterfaces = listOf(
                            InstanceNetworkInterfaceSpecification {
                                associatePublicIpAddress = true
                                deviceIndex = 0
                                subnetId = vpc.subnetIds[0]
                                groups = listOf(vpc.securityGroupIds[0])
                            }
                    )
                }
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
                val sshPrivateKey = keyPairResponse.keyMaterial

                log.info { "Successfully started EC2 Instance $instanceId based on AMI $amiId" }
                val vmOutput = VirtualMachineOutput(instanceId.toString(), ipAddress.toString(), sshPrivateKey.toString())
                return BlockOutput(block.id, block.type, inputRegion, vmOutput, null, null)
            }
        } catch (ex: Ec2Exception) {
            CommonUtils.handleAwsException(ex)
            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun startInstanceSc(instanceId: String, inputRegion: String, awsConfiguration: AwsConfiguration) {
        val request = StartInstancesRequest {
            instanceIds = listOf(instanceId)
        }

        Ec2Client {
            region = inputRegion
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { ec2 ->
            ec2.startInstances(request)
            log.info { "Waiting until instance $instanceId starts. This will take a few minutes." }
            ec2.waitUntilInstanceRunning { // suspend call
                instanceIds = listOf(instanceId)
            }
            log.info { "Successfully started instance $instanceId" }
        }
    }

    suspend fun terminateEC2(instanceID: String, inputRegion: String, awsConfiguration: AwsConfiguration) {

        val request = TerminateInstancesRequest {
            instanceIds = listOf(instanceID)
        }

        Ec2Client {
            region = inputRegion
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { ec2 ->
            val response = ec2.terminateInstances(request)
            response.terminatingInstances?.forEach { instance ->
                log.info { "The ID of the terminated instance is ${instance.instanceId}" }
            }
        }
    }
}