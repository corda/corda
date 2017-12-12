package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("nonce", "serviceKey")
@JsonInclude(NON_NULL)
class ChallengeResponse(
    @param:JsonProperty("nonce")
    val nonce: String,

    @param:JsonProperty("serviceKey")
    val serviceKey: ByteArray
)
