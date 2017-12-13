package net.corda.sgx.attestation.service.messages

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@Suppress("KDocMissingDocumentation")
@JsonPropertyOrder(
        "gb",
        "spid",
        "linkableQuote",
        "keyDerivationFuncId",
        "signatureGbGa",
        "aesCMAC",
        "revocationList"
)
class Message2(
        // The server's 512 bit public DH key (Little Endian)
        @param:JsonProperty("gb")
        val gb: ByteArray,

        @param:JsonProperty("spid")
        val spid: String,

        @param:JsonProperty("linkableQuote")
        val linkableQuote: Boolean = true,

        @param:JsonProperty("keyDerivationFuncId")
        val keyDerivationFuncId: Int,

        @param:JsonProperty("signatureGbGa")
        val signatureGbGa: ByteArray, // Not ASN.1 encoded

        @param:JsonProperty("aesCMAC")
        val aesCMAC: ByteArray,  // Not ASN.1 encoded

        @param:JsonProperty("revocationList")
        val revocationList: ByteArray

)