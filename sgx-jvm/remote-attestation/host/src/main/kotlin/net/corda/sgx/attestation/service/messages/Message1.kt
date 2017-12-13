package net.corda.sgx.attestation.service.messages

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@Suppress("KDocMissingDocumentation")
@JsonPropertyOrder("ga", "platformGID")
class Message1(

        // The client's 512 bit public DH key
        @param:JsonProperty("ga")
        val ga: ByteArray,

        // Platform GID value from the SGX client
        @param:JsonProperty("platformGID")
        val platformGID: String

)