package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("ga", "platformGID")
class Message1(
    // The client's 512 bit public DH key (Little Endian)
    @param:JsonProperty("ga")
    val ga: ByteArray,

    // Platform GID value from the SGX client
    @param:JsonProperty("platformGID")
    val platformGID: String
)
