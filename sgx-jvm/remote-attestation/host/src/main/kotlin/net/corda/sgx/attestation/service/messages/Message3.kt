package net.corda.sgx.attestation.service.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@Suppress("KDocMissingDocumentation")
@JsonPropertyOrder("aesCMAC", "ga", "quote", "securityManifest", "nonce")
@JsonInclude(NON_NULL)
class Message3(
        @param:JsonProperty("aesCMAC")
        val aesCMAC: ByteArray,  // Not ASN.1 encoded

        // The client's 512 bit public DH key
        @param:JsonProperty("ga")
        val ga: ByteArray,

        @param:JsonProperty("quote")
        val quote: ByteArray,

        @param:JsonProperty("securityManifest")
        val securityManifest: ByteArray? = null,

        @param:JsonProperty("nonce")
        val nonce: String? = null

)