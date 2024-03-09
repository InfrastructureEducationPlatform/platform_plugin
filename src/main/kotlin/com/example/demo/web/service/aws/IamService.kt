package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.iam.IamClient
import aws.sdk.kotlin.services.iam.model.*
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.ListObjectsRequest
import aws.sdk.kotlin.services.sts.StsClient
import aws.sdk.kotlin.services.sts.model.AssumeRoleRequest
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.dto.AwsConfiguration
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service

@Service
class IamService {
    suspend fun createIamRole(awsConfiguration: AwsConfiguration) {
        try {
            createElasticBeanstalkEC2Role("aws-elasticbeanstalk-ec2-role", awsConfiguration)
        } catch (_:EntityAlreadyExistsException) {
            log.info { "aws-elasticbeanstalk-ec2-role is already exist" }
        }
        try {
            createElasticBeanstalkServiceRole("aws-elasticbeanstalk-service-role", awsConfiguration)
        } catch (_:EntityAlreadyExistsException) {
            log.info { "aws-elasticbeanstalk-service-role is already exist" }
        }
    }
    private suspend fun createElasticBeanstalkEC2Role(roleName: String, awsConfiguration: AwsConfiguration) {
        val jsonString = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": [
                            "sts:AssumeRole"
                        ],
                        "Principal": {
                            "Service": [
                                "ec2.amazonaws.com"
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()
        val roleArn = createRole(roleName, jsonString, awsConfiguration)
        log.info {"$roleArn was successfully created." }
        attachRolePolicy(roleName, "arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier", awsConfiguration)
        attachRolePolicy(roleName, "arn:aws:iam::aws:policy/AWSElasticBeanstalkWorkerTier", awsConfiguration)
        attachRolePolicy(roleName, "arn:aws:iam::aws:policy/AWSElasticBeanstalkMulticontainerDocker", awsConfiguration)
    }

    private suspend fun createElasticBeanstalkServiceRole(roleName: String, awsConfiguration: AwsConfiguration) {
        val jsonString = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "",
                        "Effect": "Allow",
                        "Principal": {
                            "Service": "elasticbeanstalk.amazonaws.com"
                        },
                        "Action": "sts:AssumeRole",
                        "Condition": {
                            "StringEquals": {
                                "sts:ExternalId": "elasticbeanstalk"
                            }
                        }
                    }
                ]
            }
            """.trimIndent()
        val roleArn = createRole(roleName, jsonString, awsConfiguration)
        log.info {"$roleArn was successfully created." }
        attachRolePolicy(roleName, "arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkEnhancedHealth", awsConfiguration)
        attachRolePolicy(roleName, "arn:aws:iam::aws:policy/AWSElasticBeanstalkManagedUpdatesCustomerRolePolicy", awsConfiguration)
    }
    private suspend fun createUser(usernameVal: String?, awsConfiguration: AwsConfiguration): String? {
        val request = CreateUserRequest {
            userName = usernameVal
        }

        val iamClient = IamClient {
            region = "AWS_GLOBAL"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }
        val response = iamClient.createUser(request)
        return response.user?.userName
    }

    private suspend fun createRole(rolenameVal: String?, roleJsonVal: String, awsConfiguration: AwsConfiguration): String? {
        val request = CreateRoleRequest {
            roleName = rolenameVal
            assumeRolePolicyDocument = roleJsonVal
            description = "Created using the AWS SDK for Kotlin"
        }
        val iamClient = IamClient {
            region = "AWS_GLOBAL"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }
        val response = iamClient.createRole(request)
        try {
            iamClient.createInstanceProfile(CreateInstanceProfileRequest {
                instanceProfileName = rolenameVal
            })
        }catch (_: EntityAlreadyExistsException) {}

        delay(1000)
        iamClient.addRoleToInstanceProfile(AddRoleToInstanceProfileRequest {
            instanceProfileName = rolenameVal
            roleName = rolenameVal
        })
        return response.role?.arn
    }

    private suspend fun attachRolePolicy(roleNameVal: String, policyArnVal: String, awsConfiguration: AwsConfiguration) {

        val request = ListAttachedRolePoliciesRequest {
            roleName = roleNameVal
        }

        val iamClient = IamClient {
            region = "AWS_GLOBAL"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }
        val response = iamClient.listAttachedRolePolicies(request)
        val attachedPolicies = response.attachedPolicies

        // Ensure that the policy is not attached to this role.
        val checkStatus: Int
        if (attachedPolicies != null) {
            checkStatus = checkMyList(attachedPolicies, policyArnVal)
            if (checkStatus == -1)
                return
        }

        val policyRequest = AttachRolePolicyRequest {
            roleName = roleNameVal
            policyArn = policyArnVal
        }
        iamClient.attachRolePolicy(policyRequest)
        log.info { "Successfully attached policy $policyArnVal to role $roleNameVal" }

    }

    private fun checkMyList(attachedPolicies: List<AttachedPolicy>, policyArnVal: String): Int {
        for (policy in attachedPolicies) {
            val polArn = policy.policyArn.toString()

            if (polArn.compareTo(policyArnVal) == 0) {
                log.info { "The policy is already attached to this role." }
                return -1
            }
        }
        return 0
    }

    suspend fun assumeGivenRole(roleArnVal: String?, roleSessionNameVal: String?, bucketName: String, awsConfiguration: AwsConfiguration) {

        val stsClient = StsClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }

        val roleRequest = AssumeRoleRequest {
            roleArn = roleArnVal
            roleSessionName = roleSessionNameVal
        }

        val roleResponse = stsClient.assumeRole(roleRequest)
        val myCreds = roleResponse.credentials
        val key = myCreds?.accessKeyId
        val secKey = myCreds?.secretAccessKey
        val secToken = myCreds?.sessionToken

        val staticCredentials = StaticCredentialsProvider {
            accessKeyId = key
            secretAccessKey = secKey
            sessionToken = secToken
        }

        // List all objects in an Amazon S3 bucket using the temp creds.
        val s3 = S3Client {
            credentialsProvider = staticCredentials
            region = awsConfiguration.region
        }

        log.info { "Created a S3Client using temp credentials." }
        log.info { "Listing objects in $bucketName" }

        val listObjects = ListObjectsRequest {
            bucket = bucketName
        }

        val response = s3.listObjects(listObjects)
        response.contents?.forEach { myObject ->
            log.info { "The name of the key is ${myObject.key}" }
            log.info { "The owner is ${myObject.owner}" }
        }
    }

    suspend fun deleteRole(roleNameVal: String, polArn: String, awsConfiguration: AwsConfiguration) {

        val iamClient = IamClient {
            region = "AWS_GLOBAL"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }

        // First the policy needs to be detached.
        val rolePolicyRequest = DetachRolePolicyRequest {
            policyArn = polArn
            roleName = roleNameVal
        }

        iamClient.detachRolePolicy(rolePolicyRequest)

        // Delete the policy.
        val request = DeletePolicyRequest {
            policyArn = polArn
        }

        iamClient.deletePolicy(request)
        log.info { "*** Successfully deleted $polArn" }

        // Delete the role.
        val roleRequest = DeleteRoleRequest {
            roleName = roleNameVal
        }

        iamClient.deleteRole(roleRequest)
        log.info { "*** Successfully deleted $roleNameVal" }
    }

    suspend fun deleteUser(userNameVal: String, awsConfiguration: AwsConfiguration) {
        val iamClient = IamClient {
            region = "AWS_GLOBAL"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }
        val request = DeleteUserRequest {
            userName = userNameVal
        }

        iamClient.deleteUser(request)
        log.info { "*** Successfully deleted $userNameVal" }
    }
}