package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("gb", "signatureGbGa", "aesCMAC")
class AttestationRequest(
    // The challenger's public DH key (Little Endian)
    @param:JsonProperty("gb")
    val gb: ByteArray,

    @param:JsonProperty("signatureGbGa")
    val signatureGbGa: ByteArray,

    @param:JsonProperty("aesCMAC")
    val aesCMAC: ByteArray
)