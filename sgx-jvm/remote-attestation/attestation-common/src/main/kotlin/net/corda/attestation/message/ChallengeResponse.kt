package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("ga", "spid", "quoteType")
class ChallengeResponse(
    // The host's public DH key (Little Endian)
    @param:JsonProperty("ga")
    val ga: ByteArray,

    @param:JsonProperty("spid")
    val spid: String,

    @param:JsonProperty("quoteType")
    val quoteType: Short
)
