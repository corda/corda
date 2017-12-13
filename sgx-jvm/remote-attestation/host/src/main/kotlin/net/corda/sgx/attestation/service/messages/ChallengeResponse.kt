package net.corda.sgx.attestation.service.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@Suppress("KDocMissingDocumentation")
@JsonPropertyOrder("nonce", "serviceKey")
@JsonInclude(NON_NULL)
class ChallengeResponse(

        @param:JsonProperty("nonce")
        val nonce: String,

        @param:JsonProperty("serviceKey")
        val serviceKey: ByteArray

)
