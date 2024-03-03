package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.ec2.*
import aws.sdk.kotlin.services.ec2.model.*
import com.example.demo.web.dto.AwsConfiguration
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

data class CreateVpcDto(
    val vpcId: String,
    val cidrBlock: String,
    val subnetIds: List<String>,
    val securityGroupIds: List<String>
)

@Service
class VpcService {
    suspend fun createVpc(awsConfiguration: AwsConfiguration): CreateVpcDto {
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
            return CreateVpcDto(
                    vpcId = vpcQuery.vpcs!![0].vpcId!!,
                    cidrBlock = vpcQuery.vpcs!![0].cidrBlock!!,
                    subnetIds = ec2Client.describeSubnets {
                        filters = listOf(
                                Filter {
                                    name = "vpc-id"
                                    values = listOf(vpcQuery.vpcs!![0].vpcId!!)
                                }
                        )
                    }.subnets!!.map { it.subnetId!! },
                    securityGroupIds = ec2Client.describeSecurityGroups {
                        filters = listOf(
                            Filter {
                                name = "vpc-id"
                                values = listOf(vpcQuery.vpcs!![0].vpcId!!)
                            }
                        )
                    }.securityGroups!!.map { it.groupId!! }
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

        val vpcIdVal = vpcResponse.vpc!!.vpcId!!
        delay(1000)
        ec2Client.modifyVpcAttribute(ModifyVpcAttributeRequest {
            vpcId = vpcIdVal
            enableDnsHostnames = AttributeBooleanValue{ value = true }
        })
        val subnetIds =
            listOf(
                createSubnet(ec2Client, vpcIdVal, "10.0.0.0/24", "ap-northeast-2a"),
                createSubnet(ec2Client, vpcIdVal, "10.0.1.0/24", "ap-northeast-2a"),
                createSubnet(ec2Client, vpcIdVal, "10.0.2.0/24", "ap-northeast-2c"),
                createSubnet(ec2Client, vpcIdVal, "10.0.3.0/24", "ap-northeast-2c"),
            )
        delay(1000)
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
            this.vpcId = vpcIdVal
            internetGatewayId = internetGatewayResponse.internetGateway!!.internetGatewayId
        }

        val routeId = ec2Client.describeRouteTables {
            filters = listOf(
                Filter {
                    name = "vpc-id"
                    values = listOf(vpcIdVal)
                }
            )
        }.routeTables!![0].routeTableId

        delay(1000)
        ec2Client.createRoute {
            routeTableId = routeId
            destinationCidrBlock = "0.0.0.0/0"
            gatewayId = internetGatewayResponse.internetGateway!!.internetGatewayId
        }

        val vmSg = createEC2SecurityGroup(ec2Client, "iep-vm-sg", "sg for vm", vpcIdVal)
        val dbSg = createDBSecurityGroup(ec2Client, "iep-db-sg", "sg for db", vpcIdVal)

        return CreateVpcDto(
                vpcId = vpcResponse.vpc!!.vpcId!!,
                cidrBlock = vpcResponse.vpc!!.cidrBlock!!,
                subnetIds = subnetIds,
                securityGroupIds = listOf(vmSg!!, dbSg!!)
        )
    }

    suspend fun createSubnet(ec2Client: Ec2Client, vpcIdVal: String, cidrBlockVal: String, availabilityZoneVal: String): String {
        val subnetResponse = ec2Client.createSubnet {
            vpcId = vpcIdVal
            cidrBlock = cidrBlockVal
            availabilityZone = availabilityZoneVal
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
        return subnetResponse.subnet!!.subnetId!!
    }

    suspend fun createEC2SecurityGroup(ec2Client: Ec2Client, groupNameVal: String?, groupDescVal: String?, vpcIdVal: String?): String? {
        delay(1000)
        val request = CreateSecurityGroupRequest {
            groupName = groupNameVal
            description = groupDescVal
            vpcId = vpcIdVal
        }

        val resp = ec2Client.createSecurityGroup(request)
        val ipRange = IpRange {
            cidrIp = "0.0.0.0/0"
        }

        val ipPerm = IpPermission {
            ipProtocol = "tcp"
            toPort = 80
            fromPort = 80
            ipRanges = listOf(ipRange)
        }

        val ipPerm2 = IpPermission {
            ipProtocol = "tcp"
            toPort = 22
            fromPort = 22
            ipRanges = listOf(ipRange)
        }

        val authRequest = AuthorizeSecurityGroupIngressRequest {
            groupId = resp.groupId
            ipPermissions = listOf(ipPerm, ipPerm2)
        }
        ec2Client.authorizeSecurityGroupIngress(authRequest)
        println("Successfully added ingress policy to Security Group $groupNameVal")
        return resp.groupId
    }

    suspend fun createDBSecurityGroup(ec2Client: Ec2Client, groupNameVal: String?, groupDescVal: String?, vpcIdVal: String?): String? {
        delay(1000)
        val request = CreateSecurityGroupRequest {
            groupName = groupNameVal
            description = groupDescVal
            vpcId = vpcIdVal
        }

        val resp = ec2Client.createSecurityGroup(request)
        val ipRange = IpRange {
            cidrIp = "0.0.0.0/0"
        }

        val ipPerm = IpPermission {
            ipProtocol = "tcp"
            toPort = 5432
            fromPort = 5432
            ipRanges = listOf(ipRange)
        }

        val authRequest = AuthorizeSecurityGroupIngressRequest {
            groupId = resp.groupId
            ipPermissions = listOf(ipPerm)
        }
        ec2Client.authorizeSecurityGroupIngress(authRequest)
        println("Successfully added ingress policy to Security Group $groupNameVal")
        return resp.groupId
    }
}