package com.example.demo.web.client

import feign.RequestInterceptor
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@ConfigurationProperties(prefix = "api")
data class ApiConfiguration(
    var clientId: String = "",
    var url: String = "",
    var key: String = ""
)
@Configuration
@EnableConfigurationProperties(ApiConfiguration::class)
class AppConfiguration {
    @Bean
    fun apiConfiguration(): ApiConfiguration {
        return ApiConfiguration()
    }
}
@Configuration
class GithubClientConfig(
    val apiConfiguration: ApiConfiguration
) {
    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor {
            it.header("Authorization", "Bearer ${apiConfiguration.key}")
        }
    }
}