package com.example.demo.web

import aws.sdk.kotlin.services.elasticbeanstalk.ElasticBeanstalkClient
import aws.sdk.kotlin.services.elasticbeanstalk.model.ConfigurationOptionSetting
import aws.sdk.kotlin.services.elasticbeanstalk.model.CreateApplicationRequest
import aws.sdk.kotlin.services.elasticbeanstalk.model.CreateEnvironmentRequest
import aws.sdk.kotlin.services.elasticbeanstalk.model.DeleteApplicationRequest
import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.WebServerFeatures
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Service
public class WebApiService {
    suspend fun createEBInstance(@RequestParam block: Block): String {

        val applicationRequest = CreateApplicationRequest {
            description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
            applicationName = block.name
        }

        var tableArn: String
        ElasticBeanstalkClient { region = (block.features as LinkedHashMap<*, *>)["region"].toString() }.use { beanstalkClient ->
            val applicationResponse = beanstalkClient.createApplication(applicationRequest)
            tableArn = applicationResponse.application?.applicationArn.toString()
        }
        createEBEnvironment("testEnv", block.name)
        return tableArn
    }

    suspend fun createEBEnvironment(envName: String?, appName: String?): String {

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
        ElasticBeanstalkClient { region = "us-east-1" }.use { beanstalkClient ->
            val applicationResponse = beanstalkClient.createEnvironment(applicationRequest)
            envArn = applicationResponse.environmentArn.toString()
        }
        return envArn
    }

    @GetMapping("/deleteEB")
    suspend fun deleteApp(appName: String?) {

        val applicationRequest = DeleteApplicationRequest {
            applicationName = appName
            terminateEnvByForce = true
        }

        ElasticBeanstalkClient { region = "us-east-1" }.use { beanstalkClient ->
            beanstalkClient.deleteApplication(applicationRequest)
            println("The Elastic Beanstalk application was successfully deleted!")
        }
    }
}