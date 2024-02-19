package com.example.demo.utils

import aws.sdk.kotlin.runtime.AwsServiceException
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import io.github.oshai.kotlinlogging.KotlinLogging

object CommonUtils {
    val log = KotlinLogging.logger {}
    fun handleAwsException(ex: AwsServiceException) {

        val awsRequestId = ex.sdkErrorMetadata.requestId
        val httpResp = ex.sdkErrorMetadata.protocolResponse as? HttpResponse
        val errorMessage = ex.sdkErrorMetadata.errorMessage

        log.error { "requestId was: $awsRequestId" }
        log.error { "http status code was: ${httpResp?.status}" }
        log.error { "error message was: $errorMessage" }
    }
}