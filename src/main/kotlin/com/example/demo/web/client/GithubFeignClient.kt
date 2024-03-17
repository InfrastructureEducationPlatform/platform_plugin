package com.example.demo.web.client

import com.example.demo.web.dto.RequestGithubActionDto
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(name = "githubFeignClient", url = "https://api.github.com/", configuration = [GithubClientConfig::class])
interface GithubFeignClient {
    @PostMapping("repos/InfrastructureEducationPlatform/platform-terraform/dispatches")
    fun dispatch(@RequestBody requestBody: RequestGithubActionDto)
}