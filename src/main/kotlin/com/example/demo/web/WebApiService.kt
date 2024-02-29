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
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service


@Service
class WebApiService {
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

        val applicationRequest = CreateApplicationRequest {
            description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
            applicationName = block.name
        }

        try {
            var tableArn: String
            val inputRegion = block.webServerFeatures!!.region
            ElasticBeanstalkClient {
                region = inputRegion
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = awsConfiguration.accessKeyId
                    secretAccessKey = awsConfiguration.secretAccessKey
                }
            }.use { beanstalkClient ->
                val applicationResponse = beanstalkClient.createApplication(applicationRequest)
                tableArn = applicationResponse.application?.applicationArn.toString()
            }
            val envName =
                    if (block.name.length < 36) block.name + "-env"
                    else block.name.substring(0 until 36) + "-env"

            val endpoint: String = createEBEnvironment(envName, block.name, inputRegion)
            val ebOutput = WebServerOutput(block.name, endpoint)

            return BlockOutput(block.id, block.type, inputRegion, null, ebOutput, null)
        } catch (ex: ElasticBeanstalkException) {
            CommonUtils.handleAwsException(ex)

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun createEBEnvironment(envName: String?, appName: String?, inputRegion: String?): String {

        val setting1 = ConfigurationOptionSetting {
            namespace = "aws:autoscaling:launchconfiguration"
            optionName = "IamInstanceProfile"
            value = "aws-elasticbeanstalk-ec2-role"
        }

        val applicationRequest = CreateEnvironmentRequest {
            description = "An AWS Elastic Beanstalk environment created using the AWS SDK for Kotlin"
            environmentName = envName
            solutionStackName = "64bit Amazon Linux 2023 v4.1.1 running Corretto 17"
            applicationName = appName
            optionSettings = listOf(setting1)
        }

        var envArn: String


        try {
            ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
                val applicationResponse = beanstalkClient.createEnvironment(applicationRequest)
                envArn = applicationResponse.environmentArn.toString()
            }

            return waitForInstanceReady(envName, inputRegion)
        } catch (ex: ElasticBeanstalkException) {
            val awsRequestId = ex.sdkErrorMetadata.requestId
            val httpResp = ex.sdkErrorMetadata.protocolResponse as? HttpResponse

            log.error { "requestId was: $awsRequestId" }
            log.error { "http status code was: ${httpResp?.status}" }

            throw CustomException(ErrorCode.SKETCH_DEPLOYMENT_FAIL)
        }

    }

    suspend fun waitForInstanceReady(envName: String?, inputRegion: String?): String {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr: String

        val instanceRequest = DescribeEnvironmentsRequest {
            environmentNames = listOf(envName.toString())
        }

        var envEndpoint = ""
        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            while (!instanceReady) {
                val response = beanstalkClient.describeEnvironments(instanceRequest)
                val instanceList = response.environments
                if (instanceList != null) {
                    for (instance in instanceList) {
                        instanceReadyStr = instance.healthStatus.toString()
                        if (instanceReadyStr.contains("Terminated")) {
                            instanceReady = true
                            envEndpoint = instance.endpointUrl.toString()
                        } else {
                            log.info { "...$instanceReadyStr" }
                            delay(sleepTime * 1000)
                        }
                    }
                }
            }
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