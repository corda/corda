package net.corda.attestation.message.ias

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("spid", "revocationList")
@JsonInclude(NON_NULL)
class RevocationListProxyResponse (
    @param:JsonProperty("spid")
    val spid: String,

    @param:JsonProperty("revocationList")
    val revocationList: ByteArray
)
