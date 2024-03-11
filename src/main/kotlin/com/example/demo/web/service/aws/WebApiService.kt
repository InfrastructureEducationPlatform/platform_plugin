package com.example.demo.web.service.aws

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.elasticbeanstalk.ElasticBeanstalkClient
import aws.sdk.kotlin.services.elasticbeanstalk.model.*
import com.example.demo.utils.CommonUtils
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.CustomException
import com.example.demo.web.ErrorCode
import com.example.demo.web.RegexObj
import com.example.demo.web.dto.*
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.util.*


@Service
class WebApiService(
    private val iamService: IamService
) {
    suspend fun isValidWebBlock(block: Block) {
        if (block.webServerFeatures == null) {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_FEATURES)
        }
        val webFeatures = block.webServerFeatures!!
        if (webFeatures.tier == "" || webFeatures.containerMetadata.imageTags == "" || webFeatures.containerMetadata.registryUrl == "") {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_FEATURES)
        }

        val regexObj = RegexObj()
        if (!regexObj.verifyWebServerName(block.name)) {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_NAME)
        }
    }

    suspend fun createEBInstance(block: Block, awsConfiguration: AwsConfiguration, vpc: CreateVpcDto): BlockOutput {
        iamService.createIamRole(awsConfiguration)
        val applicationRequest = CreateApplicationRequest {
            description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
            applicationName = block.name
        }

        try {
            var tableArn: String
            ElasticBeanstalkClient {
                region = awsConfiguration.region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                }
            }.use { beanstalkClient ->
                val applicationResponse = beanstalkClient.createApplication(applicationRequest)
                tableArn = applicationResponse.application?.applicationArn.toString()
            }
            val appName = block.name
                .replace("_", "")
                .replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                    else it.toString()
                }

            val envName =
                    if (appName.length < 36) "$appName-env"
                    else appName.substring(0 until 36) + "-env"

            val endpoint: String = createEBEnvironment(envName, block.name, awsConfiguration, vpc)
            val ebOutput = WebServerOutput(block.name, endpoint)

            return BlockOutput(block.id, block.type, null, ebOutput, null)
        } catch (ex: ElasticBeanstalkException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun createEBEnvironment(envName: String?, appName: String?, awsConfiguration: AwsConfiguration, vpc: CreateVpcDto): String {
        val setting1 = ConfigurationOptionSetting {
            namespace = "aws:autoscaling:launchconfiguration"
            optionName = "IamInstanceProfile"
            value = "aws-elasticbeanstalk-ec2-role"
        }

        val setting2 = ConfigurationOptionSetting {
            namespace = "aws:ec2:vpc"
            optionName = "VPCId"
            value = vpc.vpcId
        }

        val setting3 = ConfigurationOptionSetting {
            namespace = "aws:ec2:vpc"
            optionName = "Subnets"
            value = vpc.subnetIds[0]
        }

        val applicationRequest = CreateEnvironmentRequest {
            description = "An AWS Elastic Beanstalk environment created using the AWS SDK for Kotlin"
            environmentName = envName
            solutionStackName = "64bit Amazon Linux 2023 v4.2.2 running Docker"
            applicationName = appName
            optionSettings = listOf(setting1, setting2, setting3)
        }

        var envArn: String


        try {
            ElasticBeanstalkClient {
                region = awsConfiguration.region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                }
            }.use { beanstalkClient ->
                val applicationResponse = beanstalkClient.createEnvironment(applicationRequest)
                envArn = applicationResponse.environmentArn.toString()
            }

            return waitForInstanceReady(envName, awsConfiguration)
        } catch (ex: ElasticBeanstalkException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun waitForInstanceReady(envName: String?, awsConfiguration: AwsConfiguration): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String

        val instanceRequest = DescribeEnvironmentsRequest {
            environmentNames = listOf(envName.toString())
        }

        val envEndpoint: String
        ElasticBeanstalkClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { beanstalkClient ->
            while (!instanceReady) {
                val response = beanstalkClient.describeEnvironments(instanceRequest)
                val instanceList = response.environments
                instanceReadyStr = instanceList?.get(0)?.healthStatus.toString()
                if (instanceReadyStr.contains("Ok")) {
                    instanceReady = true
                } else {
                    log.info { "...$instanceReadyStr" }
                    delay(sleepTime * 1000)
                }
            }
            delay(1000)
            val response = beanstalkClient.describeEnvironments(instanceRequest)
            val instanceList = response.environments

            envEndpoint = instanceList?.get(0)?.endpointUrl.toString()
            log.info { "$envName is available!" }
        }
        return envEndpoint
    }

    suspend fun deleteWebServer(block: Block, awsConfiguration: AwsConfiguration): Boolean {
        val envNames = mutableListOf<String>()
        ElasticBeanstalkClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { beanstalkClient ->
            val request = DescribeEnvironmentsRequest {
                applicationName = block.name
            }
            val response = beanstalkClient.describeEnvironments(request)
            for(env in response.environments!!) {
                env.environmentName?.let { envNames.add(it) }
            }
        }
        deleteApp(block.name, awsConfiguration)
        waitForEnvTerminated(envNames, awsConfiguration)

        return false
    }

    suspend fun waitForEnvTerminated(envNames: List<String>, awsConfiguration: AwsConfiguration) {
        val sleepTime: Long = 20
        var instanceTerminated = false
        var instanceReadyStr: String

        val instanceRequest = DescribeEnvironmentsRequest {
            environmentNames = envNames
        }

        ElasticBeanstalkClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { beanstalkClient ->
            while (!instanceTerminated) {
                val response = beanstalkClient.describeEnvironments(instanceRequest)
                val instanceList = response.environments
                if(instanceList.isNullOrEmpty()) break
                instanceReadyStr = instanceList[0].status?.value.toString()
                if (instanceReadyStr.contains("Terminated")) {
                    instanceTerminated = true
                } else {
                    log.info { "...$instanceReadyStr" }
                    delay(sleepTime * 1000)
                }
            }
            delay(1000)
            log.info {"The Elastic Beanstalk Environment was successfully terminated!"}
        }
    }

    suspend fun deleteApp(appName: String?, awsConfiguration: AwsConfiguration) {

        val applicationRequest = DeleteApplicationRequest {
            applicationName = appName
            terminateEnvByForce = true
        }

        ElasticBeanstalkClient {
            region = awsConfiguration.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = awsConfiguration.accessKeyId
                secretAccessKey = awsConfiguration.secretAccessKey
            }
        }.use { beanstalkClient ->
            beanstalkClient.deleteApplication(applicationRequest)
            log.info { "The Elastic Beanstalk application was successfully deleted!" }
        }
    }
}