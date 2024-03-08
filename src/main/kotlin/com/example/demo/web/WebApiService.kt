package com.example.demo.web

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.elasticbeanstalk.ElasticBeanstalkClient
import aws.sdk.kotlin.services.elasticbeanstalk.model.*
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import com.example.demo.utils.CommonUtils
import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.dto.AwsConfiguration
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.WebServerOutput
import com.example.demo.web.service.aws.IamService
import com.example.demo.web.service.aws.VpcService
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.util.*


@Service
class WebApiService(
    private val vpcService: VpcService,
    private val iamService: IamService
) {
    suspend fun isValidWebBlock(block: Block) {
        if (block.webServerFeatures == null) {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_FEATURES)
        }
        val webFeatures = block.webServerFeatures!!
        if (webFeatures.region == "" || webFeatures.tier == "" || webFeatures.containerMetadata.imageTags == "" || webFeatures.containerMetadata.registryUrl == "") {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_FEATURES)
        }

        val regexObj = RegexObj()
        if (!regexObj.verifyWebServerName(block.name)) {
            throw CustomException(ErrorCode.INVALID_WEBSERVER_NAME)
        }
    }

    suspend fun createEBInstance(block: Block, awsConfiguration: AwsConfiguration): BlockOutput {
        iamService.createIamRole()
        val applicationRequest = CreateApplicationRequest {
            description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
            applicationName = block.name
        }

        try {
            var tableArn: String
            val inputRegion = "ap-northeast-2"
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

            val endpoint: String = createEBEnvironment(envName, block.name, inputRegion, awsConfiguration)
            val ebOutput = WebServerOutput(block.name, endpoint)

            return BlockOutput(block.id, block.type, inputRegion, null, ebOutput, null)
        } catch (ex: ElasticBeanstalkException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun createEBEnvironment(envName: String?, appName: String?, inputRegion: String?, awsConfiguration: AwsConfiguration): String {
        val vpc = vpcService.createVpc(awsConfiguration)
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
            solutionStackName = "64bit Amazon Linux 2023 v4.2.1 running Corretto 21"
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

            return waitForInstanceReady(envName, inputRegion, awsConfiguration)
        } catch (ex: ElasticBeanstalkException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun waitForInstanceReady(envName: String?, inputRegion: String?, awsConfiguration: AwsConfiguration): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String

        val instanceRequest = DescribeEnvironmentsRequest {
            environmentNames = listOf(envName.toString())
        }

        val envEndpoint: String
        ElasticBeanstalkClient {
            region = inputRegion
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
        }
        return envEndpoint
    }

    suspend fun deleteApp(appName: String?, inputRegion: String?) {

        val applicationRequest = DeleteApplicationRequest {
            applicationName = appName
            terminateEnvByForce = true
        }

        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            beanstalkClient.deleteApplication(applicationRequest)
            log.info { "The Elastic Beanstalk application was successfully deleted!" }
        }
    }
}