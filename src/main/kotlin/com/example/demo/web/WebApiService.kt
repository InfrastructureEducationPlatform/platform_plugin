package com.example.demo.web

import aws.sdk.kotlin.services.elasticbeanstalk.ElasticBeanstalkClient
import aws.sdk.kotlin.services.elasticbeanstalk.model.*
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.WebServerOutput
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Service
public class WebApiService {
    suspend fun createEBInstance(@RequestParam block: Block): BlockOutput {

        val applicationRequest = CreateApplicationRequest {
            description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
            applicationName = block.name
        }

        var tableArn: String
        val inputRegion = (block.features as LinkedHashMap<*, *>)["region"].toString()
        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            val applicationResponse = beanstalkClient.createApplication(applicationRequest)
            tableArn = applicationResponse.application?.applicationArn.toString()
        }
        val endpoint:String = createEBEnvironment("testEnv", block.name, inputRegion)
        val ebOutput = WebServerOutput(block.name, endpoint)

        return BlockOutput(block.id, block.type, inputRegion, ebOutput)
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
        var envEndpoint: String



        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            val applicationResponse = beanstalkClient.createEnvironment(applicationRequest)
            envEndpoint = applicationResponse.endpointUrl.toString()
            envArn = applicationResponse.environmentArn.toString()
        }
        waitForInstanceReady(envName, inputRegion)

        return envEndpoint
    }

    suspend fun waitForInstanceReady(envName: String?, inputRegion: String?) {
        val sleepTime: Long = 20
        var instanceReady = false
        var instanceReadyStr = ""

        val instanceRequest = DescribeEnvironmentsRequest {
            environmentNames = listOf(envName.toString())
        }

        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            while (!instanceReady) {
                val response = beanstalkClient.describeEnvironments(instanceRequest)
                val instanceList = response.environments
                if (instanceList != null) {
                    for (instance in instanceList) {
                        instanceReadyStr = instance.status.toString()
                        if (instanceReadyStr.contains("Ready")) {
                            instanceReady = true
                        } else {
                            println("...$instanceReadyStr")
                            delay(sleepTime * 1000)
                        }
                    }
                }
            }
        }

    }

    @GetMapping("/deleteEB")
    suspend fun deleteApp(appName: String?, inputRegion: String?) {

        val applicationRequest = DeleteApplicationRequest {
            applicationName = appName
            terminateEnvByForce = true
        }

        ElasticBeanstalkClient { region = inputRegion }.use { beanstalkClient ->
            beanstalkClient.deleteApplication(applicationRequest)
            println("The Elastic Beanstalk application was successfully deleted!")
        }
    }
}