package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("spid", "quoteType")
class ServiceResponse(
    @param:JsonProperty("spid")
    val spid: String,

    @param:JsonProperty("quoteType")
    val quoteType: Short
)
