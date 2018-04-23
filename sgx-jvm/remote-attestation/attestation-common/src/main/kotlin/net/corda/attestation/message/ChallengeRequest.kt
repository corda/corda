package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("gc", "nonce")
@JsonInclude(NON_NULL)
class ChallengeRequest(
    // The challenger's permanent public key (Little Endian).
    @param:JsonProperty("gc")
    val gc: ByteArray,

    @param:JsonProperty("nonce")
    val nonce: String
)
