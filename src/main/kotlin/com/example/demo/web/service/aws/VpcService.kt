package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.ec2.*
import aws.sdk.kotlin.services.ec2.model.*
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.dto.AwsConfiguration
import com.example.demo.web.dto.CreateVpcDto
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service

@Service
class VpcService {
    suspend fun createVpc(awsConfiguration: AwsConfiguration): CreateVpcDto {
        val ec2Client = createEc2Client(awsConfiguration)

        val vpcId = findOrCreateVpc(ec2Client)
        val subnetIds = createSubnets(ec2Client, vpcId)
        val internetGatewayId = createInternetGateway(ec2Client, vpcId)
        val securityGroupIds = createSecurityGroups(ec2Client, vpcId)
        createRouteTable(ec2Client, vpcId, internetGatewayId)

        return CreateVpcDto(vpcId, "", subnetIds, securityGroupIds)
    }

    private fun createEc2Client(awsConfiguration: AwsConfiguration): Ec2Client {
        return Ec2Client {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }
    }

    private suspend fun findOrCreateVpc(ec2Client: Ec2Client): String {
        val vpcQuery = ec2Client.describeVpcs {
            filters = listOf(Filter {
                name = "tag:Name"
                values = listOf("deployment-vpc")
            })
        }

        if (vpcQuery.vpcs?.isNotEmpty() == true) {
            val vpc = vpcQuery.vpcs!![0]
            return vpc.vpcId ?: throw IllegalStateException("VPC ID not found")
        }

        val vpcResponse = ec2Client.createVpc {
            cidrBlock = "10.0.0.0/16"
            tagSpecifications = listOf(TagSpecification {
                resourceType = ResourceType.Vpc
                tags = listOf(Tag {
                    key = "Name"
                    value = "deployment-vpc"
                })
            })
        }

        val vpcId = vpcResponse.vpc?.vpcId
        if (vpcId != null) {
            delay(1000)
            ec2Client.modifyVpcAttribute(ModifyVpcAttributeRequest {
                this.vpcId = vpcId
                enableDnsHostnames = AttributeBooleanValue { value = true }
            })
            return vpcId
        } else {
            throw IllegalStateException("Failed to create VPC")
        }
    }

    private suspend fun createSubnets(ec2Client: Ec2Client, vpcId: String): List<String> {
        val subnetQuery = ec2Client.describeSubnets {
            filters = listOf(Filter {
                name = "tag:Name"
                values = listOf("deployment-subnet")
            })
        }

        val subnetIds = mutableListOf<String>()

        if (subnetQuery.subnets?.isNotEmpty() == true) {
            val subnets = subnetQuery.subnets
            for (subnet in subnets!!) {
                val subnetId = subnet.subnetId
                subnetId?.let { subnetIds.add(it) }
            }
            return subnetIds
        }


        val subnetConfigs = listOf(
            SubnetConfig("10.0.0.0/24", "ap-northeast-2a"),
            SubnetConfig("10.0.1.0/24", "ap-northeast-2a"),
            SubnetConfig("10.0.2.0/24", "ap-northeast-2c"),
            SubnetConfig("10.0.3.0/24", "ap-northeast-2c")
        )

        for (config in subnetConfigs) {
            val subnetId = createSubnet(ec2Client, vpcId, config)
            subnetIds.add(subnetId)
        }

        return subnetIds
    }

    private suspend fun createSubnet(ec2Client: Ec2Client, vpcId: String, config: SubnetConfig): String {
        val subnetResponse = ec2Client.createSubnet {
            this.vpcId = vpcId
            cidrBlock = config.cidrBlock
            availabilityZone = config.availabilityZone
            tagSpecifications = listOf(TagSpecification {
                resourceType = ResourceType.Subnet
                tags = listOf(Tag {
                    key = "Name"
                    value = "deployment-subnet"
                })
            })
        }
        delay(1000)
        ec2Client.modifySubnetAttribute {
            subnetId = subnetResponse.subnet?.subnetId
            mapPublicIpOnLaunch = AttributeBooleanValue { value = true }
        }
        return subnetResponse.subnet?.subnetId ?: throw IllegalStateException("Subnet ID not found")
    }

    private suspend fun createInternetGateway(ec2Client: Ec2Client, vpcId: String): String {
        val internetGatewayResponse = ec2Client.createInternetGateway {
            tagSpecifications = listOf(TagSpecification {
                resourceType = ResourceType.InternetGateway
                tags = listOf(Tag {
                    key = "Name"
                    value = "deployment-internet-gateway"
                })
            })
        }

        ec2Client.attachInternetGateway {
            this.vpcId = vpcId
            internetGatewayId = internetGatewayResponse.internetGateway!!.internetGatewayId
        }

        return internetGatewayResponse.internetGateway!!.internetGatewayId!!
    }

    private suspend fun createRouteTable(ec2Client: Ec2Client, vpcId: String, internetGatewayId: String) {
        val routeId = ec2Client.describeRouteTables {
            filters = listOf(Filter {
                name = "vpc-id"
                values = listOf(vpcId)
            })
        }.routeTables!![0].routeTableId

        delay(1000)
        ec2Client.createRoute {
            routeTableId = routeId
            destinationCidrBlock = "0.0.0.0/0"
            gatewayId = internetGatewayId
        }
    }

    private suspend fun createSecurityGroups(ec2Client: Ec2Client, vpcId: String): List<String> {
        val vmSgId = createSecurityGroup(ec2Client, "iep-vm-sg", "security group for vm", vpcId, listOf(20, 80))
        val dbSgId = createSecurityGroup(ec2Client, "iep-db-sg", "security group for db", vpcId, listOf(5432))
        return listOfNotNull(vmSgId, dbSgId)
    }

    private suspend fun createSecurityGroup(
        ec2Client: Ec2Client,
        groupName: String,
        groupDesc: String,
        vpcId: String,
        ports: List<Int>
    ): String? {
        val sgQuery = ec2Client.describeSecurityGroups {
            filters = listOf(Filter {
                name = "tag:Name"
                values = listOf(groupName)
            })
        }

        if (sgQuery.securityGroups?.isNotEmpty() == true) {
            val sg = sgQuery.securityGroups!![0]
            return sg.groupId ?: throw IllegalStateException("SecurityGroup ID not found")
        }

        delay(1000)
        val request = CreateSecurityGroupRequest {
            this.groupName = groupName
            description = groupDesc
            this.vpcId = vpcId
            tagSpecifications = listOf(TagSpecification {
                resourceType = ResourceType.SecurityGroup
                tags = listOf(Tag {
                    key = "Name"
                    value = groupName
                })
            })
        }

        val resp = ec2Client.createSecurityGroup(request)
        val ipRange = IpRange {
            cidrIp = "0.0.0.0/0"
        }

        val ipPerms = mutableListOf<IpPermission>()
        for(port in ports) {
            val ipPerm = IpPermission {
                ipProtocol = "tcp"
                toPort = port
                fromPort = port
                ipRanges = listOf(ipRange)
            }
            ipPerms.add(ipPerm)
        }

        val authRequest = AuthorizeSecurityGroupIngressRequest {
            groupId = resp.groupId
            ipPermissions = ipPerms
        }
        ec2Client.authorizeSecurityGroupIngress(authRequest)
        log.info { "Successfully added ingress policy to Security Group $groupName" }
        return resp.groupId
    }

    private data class SubnetConfig(val cidrBlock: String, val availabilityZone: String)
}