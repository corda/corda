package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("data", "authTag", "iv", "platformInfo", "aesCMAC")
@JsonInclude(NON_NULL)
class SecretRequest(
    @param:JsonProperty("data")
    val data: ByteArray,

    @param:JsonProperty("authTag")
    val authTag: ByteArray,

    @param:JsonProperty("iv")
    val iv: ByteArray,

    @param:JsonProperty("platformInfo")
    val platformInfo: ByteArray? = null,

    @param:JsonProperty("aesCMAC")
    val aesCMAC: ByteArray
)
