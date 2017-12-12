package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.time.LocalDateTime

@JsonPropertyOrder(
    "reportID",
    "quoteStatus",
    "quoteBody",
    "aesCMAC",
    "securityManifestStatus",
    "securityManifestHash",
    "platformInfo",
    "epidPseudonym",
    "nonce",
    "secret",
    "secretHash",
    "secretIV",
    "timestamp"
)
@JsonInclude(NON_NULL)
class Message4(
    @param:JsonProperty("reportID")
    val reportID: String,

    @param:JsonProperty("quoteStatus")
    val quoteStatus: String,

    @param:JsonProperty("quoteBody")
    val quoteBody: ByteArray,

    @param:JsonProperty("aesCMAC")
    val aesCMAC: ByteArray,

    @param:JsonProperty("securityManifestStatus")
    val securityManifestStatus: String? = null,

    @param:JsonProperty("securityManifestHash")
    val securityManifestHash: String? = null,

    @param:JsonProperty("platformInfo")
    @get:JsonInclude(NON_EMPTY)
    val platformInfo: ByteArray? = null,

    @param:JsonProperty("epidPseudonym")
    val epidPseudonym: ByteArray? = null,

    @param:JsonProperty("nonce")
    val nonce: String? = null,

    @param:JsonProperty("secret")
    val secret: ByteArray,

    @param:JsonProperty("secretHash")
    val secretHash: ByteArray,

    @param:JsonProperty("secretIV")
    val secretIV: ByteArray,

    @param:JsonProperty("timestamp")
    @field:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    val timestamp: LocalDateTime
)
