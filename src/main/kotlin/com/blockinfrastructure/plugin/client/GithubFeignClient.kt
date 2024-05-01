package com.blockinfrastructure.plugin.client

import com.blockinfrastructure.plugin.dto.request.DispatchGithubActionRequestDto
import feign.RequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(name = "githubFeignClient", url = "https://api.github.com/", configuration = [GithubClientConfig::class])
interface GithubFeignClient {
    @PostMapping("repos/InfrastructureEducationPlatform/platform-terraform/dispatches")
    fun dispatchAws(@RequestBody requestBody: DispatchGithubActionRequestDto)

    @PostMapping("repos/InfrastructureEducationPlatform/platform-terraform-azure/dispatches")
    fun dispatchAzure(@RequestBody requestBody: DispatchGithubActionRequestDto)
}

class GithubClientConfig(
        @Value("\${api.key}") private val githubApiKey: String,
) {
    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor {
            it.header("Authorization", "Bearer $githubApiKey")
        }
    }
}