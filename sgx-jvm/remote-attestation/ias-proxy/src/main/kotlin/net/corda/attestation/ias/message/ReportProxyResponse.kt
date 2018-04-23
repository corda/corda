package net.corda.attestation.ias.message

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("signature", "certificatePath", "report")
@JsonInclude(NON_NULL)
class ReportProxyResponse(
    @param:JsonProperty("signature")
    val signature: String,

    @param:JsonProperty("certificatePath")
    val certificatePath: String,

    @param:JsonProperty("report")
    val report: ByteArray
)
