package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.ec2.*
import aws.sdk.kotlin.services.ec2.model.Filter
import aws.sdk.kotlin.services.ec2.model.ResourceType
import aws.sdk.kotlin.services.ec2.model.Tag
import aws.sdk.kotlin.services.ec2.model.TagSpecification
import com.example.demo.web.dto.AwsConfiguration
import org.springframework.stereotype.Service

data class CreateVpcSubnetDto(
        val vpcId: String,
        val cidrBlock: String,
        val subnetIds: List<String>
)

@Service
class VpcService {
    suspend fun createVpc(awsConfiguration: AwsConfiguration): CreateVpcSubnetDto {
        val ec2Client = Ec2Client {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }

        val vpcQuery = ec2Client.describeVpcs {
            filters = listOf(
                    Filter {
                        name = "tag:Name"
                        values = listOf("deployment-vpc")
                    }
            )
        }

        // Check vpc exists
        if (vpcQuery.vpcs?.size!! > 0) {
            return CreateVpcSubnetDto(
                    vpcId = vpcQuery.vpcs!![0].vpcId!!,
                    cidrBlock = vpcQuery.vpcs!![0].cidrBlock!!,
                    subnetIds = ec2Client.describeSubnets {
                        filters = listOf(
                                Filter {
                                    name = "vpc-id"
                                    values = listOf(vpcQuery.vpcs!![0].vpcId!!)
                                }
                        )
                    }.subnets!!.map { it.subnetId!! }
            )
        }

        val vpcResponse = ec2Client.createVpc {
            cidrBlock = "10.0.0.0/16"
            tagSpecifications = listOf(
                    TagSpecification {
                        resourceType = ResourceType.Vpc
                        tags = listOf(
                                Tag {
                                    key = "Name"
                                    value = "deployment-vpc"
                                }
                        )
                    }
            )
        }

        val subnetResponse = ec2Client.createSubnet {
            vpcId = vpcResponse.vpc!!.vpcId
            cidrBlock = "10.0.0.0/24"
            availabilityZone = "ap-northeast-2a"
            tagSpecifications = listOf(
                    TagSpecification {
                        resourceType = ResourceType.Subnet
                        tags = listOf(
                                Tag {
                                    key = "Name"
                                    value = "deployment-subnet"
                                }
                        )
                    }
            )
        }

        val internetGatewayResponse = ec2Client.createInternetGateway {
            tagSpecifications = listOf(
                    TagSpecification {
                        resourceType = ResourceType.InternetGateway
                        tags = listOf(
                                Tag {
                                    key = "Name"
                                    value = "deployment-internet-gateway"
                                }
                        )
                    }
            )
        }

        ec2Client.attachInternetGateway {
            vpcId = vpcResponse.vpc!!.vpcId
            internetGatewayId = internetGatewayResponse.internetGateway!!.internetGatewayId
        }

        return CreateVpcSubnetDto(
                vpcId = vpcResponse.vpc!!.vpcId!!,
                cidrBlock = vpcResponse.vpc!!.cidrBlock!!,
                subnetIds = listOf(subnetResponse.subnet!!.subnetId!!)
        )
    }
}