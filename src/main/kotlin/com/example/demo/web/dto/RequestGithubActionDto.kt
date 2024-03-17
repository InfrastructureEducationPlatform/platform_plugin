package com.example.demo.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class RequestGithubActionDto (
    @JsonProperty("event_type")
    val eventType: String,
    @JsonProperty("client_payload")
    val clientPayload: ClientPayload
)

data class ClientPayload (
    @JsonProperty("sketch_id")
    val sketchId: String,
    @JsonProperty("tfvars_content")
    val content: String
)