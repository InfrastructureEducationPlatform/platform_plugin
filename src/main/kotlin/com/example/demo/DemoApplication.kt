package com.example.demo

import aws.sdk.kotlin.runtime.AwsServiceException
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

val log = KotlinLogging.logger {}
@SpringBootApplication
class SpringBootTutorialApplication {
	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			SpringApplication.run(SpringBootTutorialApplication::class.java, *args)
		}

		fun handleAwsException(ex: AwsServiceException) {

			val awsRequestId = ex.sdkErrorMetadata.requestId
			val httpResp = ex.sdkErrorMetadata.protocolResponse as? HttpResponse
			val errorMessage = ex.sdkErrorMetadata.errorMessage

			log.error { "requestId was: $awsRequestId" }
			log.error { "http status code was: ${httpResp?.status}" }
			log.error { "error message was: $errorMessage" }
		}
	}
}
