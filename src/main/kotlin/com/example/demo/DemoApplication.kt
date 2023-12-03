package com.example.demo

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import aws.sdk.kotlin.services.ec2.waiters.waitUntilInstanceRunning
import aws.sdk.kotlin.services.elasticbeanstalk.ElasticBeanstalkClient
import aws.sdk.kotlin.services.elasticbeanstalk.model.ConfigurationOptionSetting
import aws.sdk.kotlin.services.elasticbeanstalk.model.CreateApplicationRequest
import aws.sdk.kotlin.services.elasticbeanstalk.model.CreateEnvironmentRequest
import aws.sdk.kotlin.services.elasticbeanstalk.model.DeleteApplicationRequest
import aws.sdk.kotlin.services.rds.RdsClient
import aws.sdk.kotlin.services.rds.model.CreateDbInstanceRequest
import aws.sdk.kotlin.services.rds.model.DeleteDbInstanceRequest
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.*

@SpringBootApplication
@RestController
class SpringBootTutorialApplication {
	@PostMapping("/deploymentSketch")
	suspend fun deploymentSketch(@RequestBody sketch: RequestSketch):ResponseSketch {
		for (i in sketch.blockList) {
			when(i.type) {
				"virtualMachine"-> createEC2Instance(i.name, "ami-03d390062ea11f660")
				"webServer" -> createEBEnvironment("test_env", i.name)
				"database" -> createDatabaseInstance("test-db", i.name, "choish20", "testtest")
			}
		}

		var responseSketch:ResponseSketch = ResponseSketch()
		return responseSketch
	}
	@GetMapping("/createEC2")
	suspend fun createEC2Instance(@RequestParam name: String, @RequestParam amiId: String): String? {
		val request = RunInstancesRequest {
			imageId = amiId
			instanceType = InstanceType.T1Micro
			maxCount = 1
			minCount = 1
		}

		Ec2Client { region = "us-west-2" }.use { ec2 ->
			val response = ec2.runInstances(request)
			val instanceId = response.instances?.get(0)?.instanceId
			val tag = Tag {
				key = "Name"
				value = name
			}

			val requestTags = CreateTagsRequest {
				resources = listOf(instanceId.toString())
				tags = listOf(tag)
			}
			ec2.createTags(requestTags)
			println("Successfully started EC2 Instance $instanceId based on AMI $amiId")
			return instanceId
		}
	}

	@GetMapping("/startEC2")
	suspend fun startInstanceSc(instanceId: String) {
		val request = StartInstancesRequest {
			instanceIds = listOf(instanceId)
		}

		Ec2Client { region = "us-west-2" }.use { ec2 ->
			ec2.startInstances(request)
			println("Waiting until instance $instanceId starts. This will take a few minutes.")
			ec2.waitUntilInstanceRunning { // suspend call
				instanceIds = listOf(instanceId)
			}
			println("Successfully started instance $instanceId")
		}
	}

	@GetMapping("/terminateEC2")
	suspend fun terminateEC2(instanceID: String) {

		val request = TerminateInstancesRequest {
			instanceIds = listOf(instanceID)
		}

		Ec2Client { region = "us-west-2" }.use { ec2 ->
			val response = ec2.terminateInstances(request)
			response.terminatingInstances?.forEach { instance ->
				println("The ID of the terminated instance is ${instance.instanceId}")
			}
		}
	}

	@GetMapping("/createEB")
	suspend fun createApp(@RequestParam appName: String?): String {

		val applicationRequest = CreateApplicationRequest {
			description = "An AWS Elastic Beanstalk app created using the AWS SDK for Kotlin"
			applicationName = appName
		}

		var tableArn: String
		ElasticBeanstalkClient { region = "us-east-1" }.use { beanstalkClient ->
			val applicationResponse = beanstalkClient.createApplication(applicationRequest)
			tableArn = applicationResponse.application?.applicationArn.toString()
		}
		return tableArn
	}

	@GetMapping("/createEBEnv")
	suspend fun createEBEnvironment(envName: String?, appName: String?): String {

		val setting1 = ConfigurationOptionSetting {
			namespace = "aws:autoscaling:launchconfiguration"
			optionName = "IamInstanceProfile"
			value = "aws-elasticbeanstalk-ec2-role"
		}

		val applicationRequest = CreateEnvironmentRequest {
			description = "An AWS Elastic Beanstalk environment created using the AWS SDK for Kotlin"
			environmentName = envName
			solutionStackName = "64bit Amazon Linux 2 v3.2.12 running Corretto 11"
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

	@GetMapping("/createDB")
	suspend fun createDatabaseInstance(
		dbInstanceIdentifierVal: String?,
		dbNamedbVal: String?,
		masterUsernameVal: String?,
		masterUserPasswordVal: String?
	) {
		val instanceRequest = CreateDbInstanceRequest {
			dbInstanceIdentifier = dbInstanceIdentifierVal
			allocatedStorage = 20
			dbName = dbNamedbVal
			engine = "postgres"
			dbInstanceClass = "db.t3.micro"
			engineVersion = "15.4"
			storageType = "standard"
			masterUsername = masterUsernameVal
			masterUserPassword = masterUserPasswordVal
		}

		RdsClient { region = "us-west-2" }.use { rdsClient ->
			val response = rdsClient.createDbInstance(instanceRequest)
			print("The status is ${response.dbInstance?.dbInstanceStatus}")
		}
	}

	@GetMapping("/deleteDB")
	suspend fun deleteDatabaseInstance(dbInstanceIdentifierVal: String?) {

		val deleteDbInstanceRequest = DeleteDbInstanceRequest {
			dbInstanceIdentifier = dbInstanceIdentifierVal
			deleteAutomatedBackups = true
			skipFinalSnapshot = true
		}

		RdsClient { region = "us-west-2" }.use { rdsClient ->
			val response = rdsClient.deleteDbInstance(deleteDbInstanceRequest)
			print("The status of the database is ${response.dbInstance?.dbInstanceStatus}")
		}
	}

	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			SpringApplication.run(SpringBootTutorialApplication::class.java, *args)
		}
	}
}
