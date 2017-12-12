package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("extendedGID")
@JsonInclude(NON_NULL)
class Message0(
    @param:JsonProperty("extendedGID")
    val extendedGID: Int
)
