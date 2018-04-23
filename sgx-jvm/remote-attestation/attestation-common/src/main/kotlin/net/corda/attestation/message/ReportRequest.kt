package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("isvEnclaveQuote", "pseManifest", "nonce")
@JsonInclude(NON_NULL)
class ReportRequest(
    @param:JsonProperty("isvEnclaveQuote")
    val isvEnclaveQuote: ByteArray,

    @param:JsonProperty("pseManifest")
    val pseManifest: ByteArray? = null,

    @param:JsonProperty("nonce")
    val nonce: String? = null
)
